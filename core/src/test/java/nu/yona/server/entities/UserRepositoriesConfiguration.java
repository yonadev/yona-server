package nu.yona.server.entities;

import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;

import nu.yona.server.subscriptions.entities.UserAnonymizedRepository;
import nu.yona.server.subscriptions.entities.UserRepository;

public class UserRepositoriesConfiguration
{
	@Bean
	UserRepository getMockUserRepository()
	{
		return Mockito.spy(new UserRepositoryMock());
	}

	@Bean
	UserAnonymizedRepository getMockUserAnonymizedRepository()
	{
		return Mockito.spy(new UserAnonymizedRepositoryMock());
	}
}
