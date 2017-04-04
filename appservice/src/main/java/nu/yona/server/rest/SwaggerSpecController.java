package nu.yona.server.rest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import nu.yona.server.exceptions.YonaException;

@Controller
@RequestMapping(value = "/swagger/swagger-spec.yaml", produces = MediaType.APPLICATION_JSON_VALUE)
public class SwaggerSpecController
{
	/**
	 * Cache for the Swagger spec with a filled in host name. Multiple host names are supported, to allow accessing the same
	 * server through different paths. The spec is cached to prevent a DoS attack by fetching it many times, thus causing garbage
	 * collector stress.
	 */
	private static Map<String, String> swaggerSpecByHostName = new ConcurrentHashMap<>();

	@RequestMapping(value = "", method = RequestMethod.GET)
	@ResponseBody
	public ResponseEntity<byte[]> getSwaggerSpec(HttpServletRequest request)
	{
		String swaggerSpec = getSwaggerSpec(determineHost(request));
		return new ResponseEntity<>(swaggerSpec.getBytes(StandardCharsets.UTF_8), HttpStatus.OK);
	}

	private String getSwaggerSpec(String host)
	{
		if (swaggerSpecByHostName.containsKey(host))
		{
			return swaggerSpecByHostName.get(host);
		}
		String swaggerSpec = buildSpec(host);
		swaggerSpecByHostName.put(host, swaggerSpec);
		return swaggerSpec;
	}

	private String determineHost(HttpServletRequest request)
	{
		URI requestUri = URI.create(request.getRequestURL().toString());
		int port = requestUri.getPort();
		String host = requestUri.getHost();
		return (port == -1) ? host : host + ":" + port;
	}

	private String buildSpec(String host)
	{
		try (InputStream stream = this.getClass().getClassLoader().getResourceAsStream("swagger/swagger-spec.yaml"))
		{
			BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
			return reader.lines().map(l -> l.replace("TheHostNameToBeReplaced", host)).collect(Collectors.joining("\n"));
		}
		catch (IOException e)
		{
			throw YonaException.unexpected(e);
		}
	}
}
