/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.sms;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import nu.yona.server.exceptions.SmsException;
import nu.yona.server.properties.SmsProperties;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.util.ThymeleafUtil;

@Service
public class PlivoSmsService implements SmsService
{
	private static final Logger logger = LoggerFactory.getLogger(PlivoSmsService.class);

	@Autowired
	private YonaProperties yonaProperties;

	@Autowired
	@Qualifier("smsTemplateEngine")
	private TemplateEngine templateEngine;

	@Override
	public void send(String phoneNumber, SmsTemplate messageTemplate, Map<String, Object> templateParameters)
	{
		Context ctx = ThymeleafUtil.createContext();
		templateParameters.entrySet().stream().forEach(e -> ctx.setVariable(e.getKey(), e.getValue()));

		String message = templateEngine.process(messageTemplate.getName() + ".txt", ctx);
		logger.info("Sending SMS to number '{}'. Message: {}", phoneNumber, message);

		if (!yonaProperties.getSms().isEnabled())
		{
			logger.info("SMS sending is disabled. No message has been sent.");
			return;
		}

		try (CloseableHttpClient httpClient = HttpClientBuilder.create().build())
		{
			String requestMessageStr = createRequestJson(phoneNumber.replace("+", ""), message);
			HttpPost httpRequest = createHttpRequest(requestMessageStr);
			HttpClientContext httpClientContext = createHttpClientContext();
			HttpResponse httpResponse = httpClient.execute(httpRequest, httpClientContext);
			HttpEntity entity = httpResponse.getEntity();

			int httpResponseCode = httpResponse.getStatusLine().getStatusCode();
			String httpResponseBody = EntityUtils.toString(entity);
			if (httpResponseCode != HttpStatus.SC_ACCEPTED)
			{
				throw SmsException.smsSendingFailed(httpResponseCode, httpResponseBody);
			}
		}
		catch (IOException e)
		{
			throw SmsException.smsSendingFailed(e);
		}

		logger.info("SMS sent succesfully.");
	}

	private HttpClientContext createHttpClientContext()
	{
		try
		{
			SmsProperties smsProperties = yonaProperties.getSms();

			URI uri = getPlivoUrl(smsProperties);
			CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
			credentialsProvider.setCredentials(new AuthScope(uri.getHost(), uri.getPort()),
					new UsernamePasswordCredentials(smsProperties.getPlivoAuthId(), smsProperties.getPlivoAuthToken()));

			HttpClientContext httpClientContext = HttpClientContext.create();
			httpClientContext.setCredentialsProvider(credentialsProvider);
			return httpClientContext;
		}
		catch (URISyntaxException e)
		{
			throw SmsException.smsSendingFailed(e);
		}
	}

	private URI getPlivoUrl(SmsProperties smsProperties) throws URISyntaxException
	{
		return new URI(MessageFormat.format(smsProperties.getPlivoUrl(), smsProperties.getPlivoAuthId()));
	}

	private String createRequestJson(String phoneNumber, String message)
	{
		try
		{
			Map<String, Object> requestMessage = new HashMap<>();
			requestMessage.put("src", yonaProperties.getSms().getSenderNumber());
			requestMessage.put("dst", phoneNumber);
			requestMessage.put("text", message);

			return new ObjectMapper().writeValueAsString(requestMessage);
		}
		catch (JsonProcessingException e)
		{
			throw SmsException.smsSendingFailed(e);
		}
	}

	private HttpPost createHttpRequest(String jsonStr)
	{
		try
		{
			SmsProperties smsProperties = yonaProperties.getSms();
			URI uri = getPlivoUrl(smsProperties);

			HttpPost httpRequest = new HttpPost(uri);
			StringEntity requestEntity = new StringEntity(jsonStr, "UTF-8");

			httpRequest.setEntity(requestEntity);
			httpRequest.setHeader("Accept", "application/json");
			httpRequest.setHeader("Content-type", "application/json");

			return httpRequest;
		}
		catch (URISyntaxException e)
		{
			throw SmsException.smsSendingFailed(e);
		}
	}
}
