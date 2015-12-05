package nu.yona.server;

import java.net.URI;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import nu.yona.server.properties.YonaProperties;

@Service
public class DOSProtectionService
{
	@Autowired
	private YonaProperties yonaProperties;

	private LoadingCache<String, Integer> attemptsCache;

	@PostConstruct
	private void initializeCache()
	{
		attemptsCache = CacheBuilder.newBuilder()
				.expireAfterWrite(yonaProperties.getSecurity().getDosProtectionWindowSeconds(), TimeUnit.SECONDS)
				.build(new CacheLoader<String, Integer>() {
					public Integer load(String key)
					{
						return 0;
					}
				});
	}

	public <T> T executeAttempt(URI uri, HttpServletRequest request, int expectedAttempts, Supplier<T> attempt)
	{
		if (yonaProperties.getSecurity().isDosProtectionEnabled())
		{
			int attempts = increaseAttempts(uri, request);
			executeDelay(expectedAttempts, attempts);
		}

		return attempt.get();
	}

	private void executeDelay(int expectedAttempts, int attempts)
	{
		if (attempts > expectedAttempts)
		{
			int delayFactor = attempts / expectedAttempts;
			try
			{
				Thread.sleep(delayFactor * 5000);
			}
			catch (InterruptedException e)
			{

			}
		}
	}

	private int increaseAttempts(URI uri, HttpServletRequest request)
	{
		String key = getKey(uri, request);
		int attempts = 0;
		try
		{
			attempts = attemptsCache.get(key);
		}
		catch (ExecutionException e)
		{
			attempts = 0;
		}
		attempts++;
		attemptsCache.put(key, attempts);
		return attempts;
	}

	private String getKey(URI uri, HttpServletRequest request)
	{
		return uri.toString() + "~" + request.getRemoteAddr();
	}
}
