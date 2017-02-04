package nu.yona.server.test.util;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import java.io.Serializable;
import java.util.Map;

import org.mockito.Matchers;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.support.Repositories;

import nu.yona.server.entities.RepositoryProvider;

public class JUnitUtil
{

	private JUnitUtil()
	{
		// No instances
	}

	@SuppressWarnings("unchecked")
	public static <T, ID extends Serializable> void setUpRepositoryMock(CrudRepository<T, ID> mockRepository)
	{
		// save should not return null but the saved entity
		when(mockRepository.save(Matchers.<T> any())).thenAnswer(new Answer<T>() {
			@Override
			public T answer(InvocationOnMock invocation) throws Throwable
			{
				Object[] args = invocation.getArguments();
				return (T) args[0];
			}
		});

	}

	public static Repositories setUpRepositoryProviderMock(Map<Class<?>, Repository<?, ?>> repositoriesMap)
	{
		Repositories mockRepositories = Mockito.mock(Repositories.class);
		when(mockRepositories.getRepositoryFor(any())).thenAnswer(new Answer<Object>() {
			@Override
			public Object answer(InvocationOnMock invocation) throws Throwable
			{
				Class<?> entityClass = (Class<?>) invocation.getArguments()[0];
				Repository<?, ?> repository = repositoriesMap.get(entityClass);
				if (repository == null)
				{
					throw new IllegalArgumentException("Unsupported class: " + entityClass.getName());
				}
				return repository;
			}
		});
		RepositoryProvider.setRepositories(mockRepositories);
		return mockRepositories;
	}
}
