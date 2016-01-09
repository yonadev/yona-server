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
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.velocity.app.VelocityEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.ui.velocity.VelocityEngineUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import nu.yona.server.exceptions.SmsException;
import nu.yona.server.properties.SmsProperties;
import nu.yona.server.properties.YonaProperties;

@Service
public class PlivoSmsService implements SmsService
{
	private static final Logger logger = LoggerFactory.getLogger(PlivoSmsService.class);

	@Autowired
	private YonaProperties yonaProperties;
	@Autowired
	private VelocityEngine velocityEngine;

	@Override
	public void send(String phoneNumber, String messageTemplateName, Map<String, Object> templateParameters)
	{
		String message = VelocityEngineUtils.mergeTemplateIntoString(velocityEngine, "sms/" + messageTemplateName + ".vm",
				"UTF-8", templateParameters);

		logger.info("Sending SMS to number '{}'. Message: {}", phoneNumber, message);

		if (!yonaProperties.getSms().isEnabled())
		{
			logger.info("SMS sending is disabled. No message has been sent.");
			return;
		}

		DefaultHttpClient httpClient = new DefaultHttpClient();
		try
		{
			String requestMessageStr = createRequestJson(phoneNumber.replace("+", ""), message);
			HttpPost httpRequest = createHttpRequest(httpClient, requestMessageStr);
			HttpResponse httpResponse = httpClient.execute(httpRequest);
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
		finally
		{
			httpClient.getConnectionManager().shutdown();
		}

		logger.info("SMS sent succesfully.");
	}

	private String createRequestJson(String phoneNumber, String message)
	{
		try
		{
			Map<String, Object> requestMessage = new HashMap<String, Object>();
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

	private HttpPost createHttpRequest(DefaultHttpClient httpClient, String jsonStr)
	{
		try
		{
			SmsProperties smsProperties = yonaProperties.getSms();
			URI uri = new URI(MessageFormat.format(smsProperties.getPlivoUrl(), smsProperties.getPlivoAuthId()));
			httpClient.getCredentialsProvider().setCredentials(new AuthScope(uri.getHost(), uri.getPort()),
					new UsernamePasswordCredentials(smsProperties.getPlivoAuthId(), smsProperties.getPlivoAuthToken()));

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
