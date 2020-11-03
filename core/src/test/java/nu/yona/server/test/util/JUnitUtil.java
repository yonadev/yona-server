/*******************************************************************************
 * Copyright (c) 2017, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.test.util;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.support.Repositories;

import mockit.Mock;
import mockit.MockUp;
import nu.yona.server.Translator;
import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.exceptions.YonaException;
import nu.yona.server.messaging.entities.MessageSource;
import nu.yona.server.subscriptions.entities.Buddy;
import nu.yona.server.subscriptions.entities.BuddyAnonymized.Status;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.subscriptions.entities.UserPrivate;
import nu.yona.server.util.TimeUtil;

public class JUnitUtil
{
	private static final Field translatorStaticField = JUnitUtil.getAccessibleField(Translator.class, "staticReference");
	private static final Field userPrivateField = JUnitUtil.getAccessibleField(User.class, "userPrivate");
	private static final Field creationTimeField = JUnitUtil.getAccessibleField(UserPrivate.class, "creationTime");
	private static final Field roundedCreationDateField = JUnitUtil.getAccessibleField(User.class, "roundedCreationDate");

	private JUnitUtil()
	{
		// No instances
	}

	@SuppressWarnings("unchecked")
	public static <T, ID extends Serializable> void setUpRepositoryMock(CrudRepository<T, ID> mockRepository)
	{
		// save should not return null but the saved entity
		Mockito.lenient().when(mockRepository.save(ArgumentMatchers.<T>any())).thenAnswer(new Answer<T>()
		{
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
		when(mockRepositories.getRepositoryFor(any())).thenAnswer(new Answer<Object>()
		{
			@Override
			public Object answer(InvocationOnMock invocation) throws Throwable
			{
				Class<?> entityClass = (Class<?>) invocation.getArguments()[0];
				Repository<?, ?> repository = repositoriesMap.get(entityClass);
				if (repository == null)
				{
					throw new IllegalArgumentException("Unsupported class: " + entityClass.getName());
				}
				return Optional.of(repository);
			}
		});
		RepositoryProvider.setRepositories(mockRepositories);
		return mockRepositories;
	}

	public static User createRichard()
	{
		return createUserEntity("Richard", "Quinn", "+31610000000", "RQ");
	}

	public static User createBob()
	{
		return createUserEntity("Bob", "Dunn", "+31610000001", "BD");
	}

	public static User createUserEntity(String firstName, String lastName, String mobileNumber, String nickname)
	{
		MessageSource anonymousMessageSource = MessageSource.createInstance();
		UserAnonymized userAnonymized = UserAnonymized
				.createInstance(anonymousMessageSource.getDestination(), Collections.emptySet());
		UserAnonymized.getRepository().save(userAnonymized);

		byte[] initializationVector = CryptoSession.getCurrent().generateInitializationVector();
		MessageSource namedMessageSource = MessageSource.createInstance();
		MessageSource.getRepository().save(namedMessageSource);
		UserPrivate userPrivate = UserPrivate
				.createInstance(TimeUtil.utcNow(), firstName, lastName, nickname, userAnonymized.getId(),
						anonymousMessageSource.getId(), namedMessageSource);
		User user = new User(UUID.randomUUID(), initializationVector, TimeUtil.utcNow().toLocalDate(), mobileNumber, userPrivate,
				namedMessageSource.getDestination());
		return User.getRepository().save(user);
	}

	public static void makeBuddies(User user, User buddyToBe)
	{
		addAsBuddy(user, buddyToBe);
		addAsBuddy(buddyToBe, user);
	}

	private static void addAsBuddy(User user, User buddyToBe)
	{
		Buddy buddy = Buddy
				.createInstance(buddyToBe.getId(), buddyToBe.getFirstName(), buddyToBe.getLastName(), buddyToBe.getNickname(),
						Optional.empty(), Status.ACCEPTED, Status.ACCEPTED);
		buddy.setUserAnonymizedId(buddyToBe.getAnonymized().getId());
		user.addBuddy(buddy);
	}

	public static Field getAccessibleField(Class<?> clazz, String fieldName)
	{
		Field field = Arrays.asList(clazz.getDeclaredFields()).stream().filter(f -> f.getName().equals(fieldName)).findAny()
				.orElseThrow(() -> new IllegalStateException("Cannot find field '" + fieldName + "'"));
		field.setAccessible(true);
		return field;
	}

	public static <T> Constructor<T> getAccessibleConstructor(Class<T> clazz, Class<?>... parameterTypes)
	{
		try
		{
			Constructor<T> constructor = clazz.getDeclaredConstructor(parameterTypes);
			constructor.setAccessible(true);
			return constructor;
		}
		catch (NoSuchMethodException | SecurityException e)
		{
			throw new IllegalStateException("Cannot find constructor", e);
		}
	}

	public static LocalDateTime getRoundedCreationDate(User user)
	{
		try
		{
			return (LocalDateTime) roundedCreationDateField.get(user);
		}
		catch (IllegalArgumentException | IllegalAccessException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	public static void setRoundedCreationDate(User user, LocalDateTime time)
	{
		try
		{
			roundedCreationDateField.set(user, time);
		}
		catch (IllegalArgumentException | IllegalAccessException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	public static LocalDateTime getCreationTime(UserPrivate userPrivate)
	{
		try
		{
			return (LocalDateTime) creationTimeField.get(userPrivate);
		}
		catch (IllegalArgumentException | IllegalAccessException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	public static void setCreationTime(UserPrivate userPrivate, LocalDateTime time)
	{
		try
		{
			creationTimeField.set(userPrivate, time);
		}
		catch (IllegalArgumentException | IllegalAccessException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	public static UserPrivate getUserPrivate(User user)
	{
		try
		{
			return (UserPrivate) userPrivateField.get(user);
		}
		catch (IllegalArgumentException | IllegalAccessException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	public static void setUserPrivate(User user, UserPrivate userPrivate)
	{
		try
		{
			userPrivateField.set(user, userPrivate);
		}
		catch (IllegalArgumentException | IllegalAccessException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	public static void skipBefore(String description, ZonedDateTime now, int hour, int minute)
	{
		assumeTrue(now.isAfter(now.withHour(hour).withMinute(minute).withSecond(0)), description);
	}

	public static void setupTranslatorMock(Translator translator)
	{
		try
		{
			translatorStaticField.set(null, translator);

			lenient().when(translator.getLocalizedMessage(any(String.class), any())).thenAnswer(new Answer<String>()
			{
				@Override
				public String answer(InvocationOnMock invocation) throws Throwable
				{
					Object[] args = invocation.getArguments();
					assert args.length >= 1;
					String messageId = (String) args[0];
					String insertions = String.join("/", buildStringArray(args));
					return messageId + ":" + insertions;
				}

				private String[] buildStringArray(Object[] args)
				{
					String[] retVal = new String[args.length - 1];
					for (int i = 0; (i < retVal.length); i++)
					{
						Object val = args[i + 1];
						retVal[i] = (val == null) ? "<null>" : val.toString();
					}
					return retVal;
				}
			});
		}
		catch (IllegalArgumentException | IllegalAccessException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	public static void skipAfter(String description, ZonedDateTime now, int hour, int minute)
	{
		assumeTrue(now.isBefore(now.withHour(hour).withMinute(minute).withSecond(0)), description);
	}

	public static LocalDateTime mockCurrentTime(String dateTimeUtc)
	{
		return mockCurrentTime(LocalDateTime.parse(dateTimeUtc));
	}

	public static LocalDateTime mockCurrentTime(LocalDateTime dateTime)
	{
		Clock clock = Clock.fixed(dateTime.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

		new MockUp<TimeUtil>()
		{
			@Mock
			public LocalDateTime utcNow()
			{
				return LocalDateTime.now(clock);
			}
		};

		return dateTime;
	}
}