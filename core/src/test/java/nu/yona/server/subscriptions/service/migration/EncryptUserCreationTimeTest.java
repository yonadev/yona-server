package nu.yona.server.subscriptions.service.migration;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.repository.Repository;

import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.entities.MessageSourceRepositoryMock;
import nu.yona.server.entities.UserAnonymizedRepositoryMock;
import nu.yona.server.entities.UserRepositoryMock;
import nu.yona.server.messaging.entities.MessageSource;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.subscriptions.entities.UserAnonymizedRepository;
import nu.yona.server.subscriptions.entities.UserPrivate;
import nu.yona.server.subscriptions.entities.UserRepository;
import nu.yona.server.subscriptions.service.PrivateUserDataMigrationService.MigrationStep;
import nu.yona.server.test.util.JUnitUtil;

class EncryptUserCreationTimeTest
{
	private static final String PASSWORD = "password";
	private static final LocalDateTime TEST_CREATION_TIME = LocalDateTime.parse("2019-11-26T21:15:31.043");

	private final MigrationStep migrationStep = new EncryptUserCreationTime();

	protected UserRepository userRepository;

	protected UserAnonymizedRepository userAnonymizedRepository;

	private User user;
	private UserPrivate userPrivate;

	private ZoneId userAnonZoneId;

	@BeforeEach
	public void setUpPerTest() throws Exception
	{
		setUpRepositoryMocks();

		try (CryptoSession cryptoSession = CryptoSession.start(PASSWORD))
		{
			user = JUnitUtil.createRichard();
			userPrivate = JUnitUtil.getUserPrivate(user);
			userAnonZoneId = userAnonymizedRepository.getById(user.getUserAnonymizedId()).getTimeZone();
		}

		userPrivate = Mockito.spy(userPrivate);
		JUnitUtil.setUserPrivate(user, userPrivate);

	}

	private void setUpRepositoryMocks()
	{
		userRepository = new UserRepositoryMock();
		userAnonymizedRepository = new UserAnonymizedRepositoryMock();

		Map<Class<?>, Repository<?, ?>> repositoriesMap = new HashMap<>();
		repositoriesMap.put(User.class, userRepository);
		repositoriesMap.put(UserAnonymized.class, userAnonymizedRepository);
		repositoriesMap.put(MessageSource.class, new MessageSourceRepositoryMock());
		JUnitUtil.setUpRepositoryProviderMock(repositoriesMap);
	}

	@Test
	void upgrade_moveCreationTime_creationTimeMoved()
	{
		JUnitUtil.skipAfter("Skip shortly before midnight", now(), 23, 55);

		JUnitUtil.setRoundedCreationDate(user, TEST_CREATION_TIME);
		JUnitUtil.setCreationTime(userPrivate, null);
		migrationStep.upgrade(user);

		assertThat(userPrivate.getCreationTime(), equalTo(TEST_CREATION_TIME));
		assertThat(user.getRoundedCreationDate(), equalTo(TEST_CREATION_TIME.toLocalDate()));
		assertThat(JUnitUtil.getRoundedCreationDate(user), equalTo(TEST_CREATION_TIME.toLocalDate().atStartOfDay()));
	}

	@Test
	void upgrade_againMoveCreationTime_noChange()
	{
		JUnitUtil.skipAfter("Skip shortly before midnight", now(), 23, 55);

		// Creation time is already set, so upgrade should not call setCreationTime
		migrationStep.upgrade(user);
		verify(userPrivate, never()).setCreationTime(Mockito.any());
	}

	private ZonedDateTime now()
	{
		return ZonedDateTime.now().withZoneSameInstant(userAnonZoneId);
	}
}
