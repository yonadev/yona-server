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

	public void attemptSucceeded(URI uri)
	{
		String key = getKey(uri);
		attemptsCache.invalidate(key);
	}

	public void attemptFailed(URI uri)
	{
		String key = getKey(uri);
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

	public boolean isBlocked(URI uri, int maxAttempts)
	{
		String key = getKey(uri);
		try
		{
			return attemptsCache.get(key) >= maxAttempts;
		}
		catch (ExecutionException e)
		{
			return false;
		}
	}

	private String getKey(URI uri)
	{
		String key = uri.toString();
		return key;
	}

	public <T> T executeAttempt(URI uri, int maxAttempts, Supplier<T> supplier)
	{
		throwIfBlocked(uri, maxAttempts);

		try
		{
			T result = supplier.get();
			attemptSucceeded(uri);
			return result;
		}
		catch (RuntimeException e)
		{
			attemptFailed(uri);
			throw e;
		}
	}

	public void throwIfBlocked(URI uri, int maxAttempts)
	{
		if (isBlocked(uri, maxAttempts))
		{
			throw BruteForceException.tooManyAttempts(uri);
		}
	}
}