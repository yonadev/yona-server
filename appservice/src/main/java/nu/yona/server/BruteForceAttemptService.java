package nu.yona.server;

import java.net.URI;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import nu.yona.server.properties.YonaProperties;

@Service
public class BruteForceAttemptService
{
	@Autowired
	private YonaProperties yonaProperties;

	private LoadingCache<String, Integer> attemptsCache;

	@PostConstruct
	private void initializeCache()
	{
		attemptsCache = CacheBuilder.newBuilder()
				.expireAfterWrite(yonaProperties.getSecurity().getBruteForceBlockMinutes(), TimeUnit.MINUTES)
				.build(new CacheLoader<String, Integer>() {
					public Integer load(String key)
					{
						return 0;
					}
				});
	}

	public void attemptSucceeded(URI uri, String... otherKeys)
	{
		String key = getKey(uri, otherKeys);
		attemptsCache.invalidate(key);
	}

	public void attemptFailed(URI uri, String... otherKeys)
	{
		String key = getKey(uri, otherKeys);
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
	}

	public boolean isBlocked(URI uri, int maxAttempts, String... otherKeys)
	{
		String key = getKey(uri, otherKeys);
		try
		{
			return attemptsCache.get(key) >= maxAttempts;
		}
		catch (ExecutionException e)
		{
			return false;
		}
	}

	private String getKey(URI uri, String... otherKeys)
	{
		String key = uri.toString() + String.join("~", otherKeys);
		return key;
	}

	public <T> T executeAttempt(URI uri, int maxAttempts, Supplier<T> supplier, String... otherKeys)
	{
		throwIfBlocked(uri, maxAttempts, otherKeys);

		T result = attemptExecution(uri, supplier, otherKeys);

		attemptSucceeded(uri, otherKeys);
		return result;
	}

	private <T> T attemptExecution(URI uri, Supplier<T> supplier, String... otherKeys)
	{
		try
		{
			return supplier.get();
		}
		catch (RuntimeException e)
		{
			attemptFailed(uri, otherKeys);
			throw e;
		}
	}

	public void throwIfBlocked(URI uri, int maxAttempts, String... otherKeys)
	{
		if (isBlocked(uri, maxAttempts, otherKeys))
		{
			throw BruteForceException.tooManyAttempts(uri);
		}
	}
}