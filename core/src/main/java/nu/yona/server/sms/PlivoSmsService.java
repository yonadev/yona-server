package nu.yona.server.sms;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import nu.yona.server.properties.YonaProperties;
import nu.yona.server.properties.YonaProperties.Sms;

@Service
public class PlivoSmsService implements SmsService
{
	private static final Logger LOGGER = Logger.getLogger(PlivoSmsService.class.getName());

	@Autowired
	private YonaProperties yonaProperties;

	@Override
	public void send(String phoneNumber, String message)
	{
		if (LOGGER.isLoggable(Level.INFO))
		{
			LOGGER.info(MessageFormat.format("Sending SMS to number '{0}'. Message: {0}\r\n", phoneNumber, message));
		}

		if (!yonaProperties.getSms().isEnabled())
		{
			LOGGER.info("SMS sending is disabled. No message has been sent.");
			return;
		}

		DefaultHttpClient httpClient = new DefaultHttpClient();
		int httpResponseCode;
		String httpResponseBody;

		try
		{
			String requestMessageStr = createRequestJson(phoneNumber.replace("+", ""), message);
			HttpPost httpRequest = createHttpRequest(httpClient, requestMessageStr);
			HttpResponse httpResponse = httpClient.execute(httpRequest);
			HttpEntity entity = httpResponse.getEntity();

			httpResponseCode = httpResponse.getStatusLine().getStatusCode();
			httpResponseBody = EntityUtils.toString(entity);
		}
		catch (Exception e)
		{
			throw new IllegalStateException("SMS sending failed: " + e.getMessage(), e);
		}
		finally
		{
			httpClient.getConnectionManager().shutdown();
		}

		if (httpResponseCode != 202)
		{
			throw new IllegalStateException(
					"Unexpected status code received from SMS service: " + httpResponseCode + ". Message: " + httpResponseBody);
		}

		LOGGER.info("SMS sent succesfully.");
	}

	private String createRequestJson(String phoneNumber, String message) throws JsonProcessingException
	{
		Map<String, Object> requestMessage = new HashMap<String, Object>();
		requestMessage.put("src", yonaProperties.getSms().getSenderNumber());
		requestMessage.put("dst", phoneNumber);
		requestMessage.put("text", message);

		return new ObjectMapper().writeValueAsString(requestMessage);
	}

	private HttpPost createHttpRequest(DefaultHttpClient httpClient, String jsonStr)
			throws URISyntaxException, UnsupportedEncodingException
	{
		Sms smsConfig = yonaProperties.getSms();
		URI uri = new URI(MessageFormat.format(smsConfig.getPlivoUrl(), smsConfig.getPlivoAuthId()));

		httpClient.getCredentialsProvider().setCredentials(new AuthScope(uri.getHost(), uri.getPort()),
				new UsernamePasswordCredentials(smsConfig.getPlivoAuthId(), smsConfig.getPlivoAauthToken()));

		HttpPost httpRequest = new HttpPost(uri);
		StringEntity requestEntity = new StringEntity(jsonStr, "UTF-8");

		httpRequest.setEntity(requestEntity);
		httpRequest.setHeader("Accept", "application/json");
		httpRequest.setHeader("Content-type", "application/json");

		return httpRequest;
	}
}
