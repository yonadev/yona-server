package nu.yona.server.entities;

import java.time.LocalDate;

import org.apache.commons.lang.NotImplementedException;

import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.subscriptions.entities.UserAnonymizedRepository;

public class UserAnonymizedRepositoryMock extends MockJpaRepositoryEntityWithUuid<UserAnonymized>
		implements UserAnonymizedRepository
{

	@Override
	public int countByLastMonitoredActivityDateBetween(LocalDate startDate, LocalDate endDate)
	{
		throw new NotImplementedException();
	}

	@Override
	public int countByLastMonitoredActivityDateIsNull()
	{
		throw new NotImplementedException();
	}
}