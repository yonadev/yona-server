package nu.yona.server.rest;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;

public class RestClientErrorHandler implements ResponseErrorHandler
{
	private static final Logger logger = LoggerFactory.getLogger(RestClientErrorHandler.class);

	@Override
	public void handleError(ClientHttpResponse response) throws IOException
	{
		logger.error("Response error: {} {}", response.getStatusCode(), response.getStatusText());
	}

	@Override
	public boolean hasError(ClientHttpResponse response) throws IOException
	{
		return RestUtil.isError(response.getStatusCode());
	}
}
