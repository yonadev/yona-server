/*******************************************************************************
 * Copyright (c) 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service.migration;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.MethodRule;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Matchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.data.repository.Repository;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import nu.yona.server.CoreConfiguration;
import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.entities.BuddyAnonymizedRepositoryMock;
import nu.yona.server.entities.UserRepositoriesConfiguration;
import nu.yona.server.messaging.entities.Message;
import nu.yona.server.messaging.service.MessageService;
import nu.yona.server.subscriptions.entities.BuddyAnonymized;
import nu.yona.server.subscriptions.entities.BuddyAnonymizedRepository;
import nu.yona.server.subscriptions.entities.BuddyInfoChangeMessage;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.entities.UserPrivate;
import nu.yona.server.subscriptions.service.PrivateUserDataMigrationService.MigrationStep;
import nu.yona.server.subscriptions.service.UserAnonymizedDto;
import nu.yona.server.test.util.BaseSpringIntegrationTest;
import nu.yona.server.test.util.CryptoSessionRule;
import nu.yona.server.test.util.JUnitUtil;

@Configuration
@ComponentScan(useDefaultFilters = false, basePackages = { "nu.yona.server.subscriptions.service",
		"nu.yona.server.device.service", "nu.yona.server.properties", "nu.yona.server" }, includeFilters = {
				@ComponentScan.Filter(pattern = "nu.yona.server.subscriptions.service.migration.EncryptFirstAndLastName", type = FilterType.REGEX),
				@ComponentScan.Filter(pattern = "nu.yona.server.subscriptions.service.BuddyService", type = FilterType.REGEX),
				@ComponentScan.Filter(pattern = "nu.yona.server.subscriptions.service.UserService", type = FilterType.REGEX),
				@ComponentScan.Filter(pattern = "nu.yona.server.util.TransactionHelper", type = FilterType.REGEX),
				@ComponentScan.Filter(pattern = "nu.yona.server.properties.YonaProperties", type = FilterType.REGEX),
				@ComponentScan.Filter(pattern = "nu.yona.server.Translator", type = FilterType.REGEX) })
class EncryptFirstAndLastNameTestConfiguration extends UserRepositoriesConfiguration
{
	@Bean(name = "messageSource")
	public ReloadableResourceBundleMessageSource messageSource()
	{
		return new CoreConfiguration().messageSource();
	}

	@Bean
	BuddyAnonymizedRepository getBuddyAnonymizedRepository()
	{
		return new BuddyAnonymizedRepositoryMock();
	}
}

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { EncryptFirstAndLastNameTestConfiguration.class })
public class EncryptFirstAndLastNameTest extends BaseSpringIntegrationTest
{
	private static final String PASSWORD = "password";
	private static final Field firstNameUserField = JUnitUtil.getAccessibleField(User.class, "firstName");
	private static final Field lastNameUserField = JUnitUtil.getAccessibleField(User.class, "lastName");
	private static final Field firstNameUserPrivateField = JUnitUtil.getAccessibleField(UserPrivate.class, "firstName");
	private static final Field lastNameUserPrivateField = JUnitUtil.getAccessibleField(UserPrivate.class, "lastName");
	private static final Field userPrivateField = JUnitUtil.getAccessibleField(User.class, "userPrivate");
	private User richard;

	@Autowired
	private MigrationStep migrationStep;

	@Rule
	public MethodRule cryptoSession = new CryptoSessionRule(PASSWORD);

	@Autowired
	private BuddyAnonymizedRepository buddyAnonymizedRepository;

	@MockBean
	private MessageService mockMessageService;

	@Captor
	private ArgumentCaptor<Supplier<Message>> messageSupplierCaptor;

	@Before
	public void setUpPerTest() throws Exception
	{
		try (CryptoSession cryptoSession = CryptoSession.start(PASSWORD))
		{
			richard = JUnitUtil.createRichard();
		}
	}

	@Override
	protected Map<Class<?>, Repository<?, ?>> getRepositories()
	{
		Map<Class<?>, Repository<?, ?>> repositoriesMap = new HashMap<>();
		repositoriesMap.put(BuddyAnonymized.class, buddyAnonymizedRepository);
		return repositoriesMap;
	}

	@Test
	public void upgrade_moveFirstAndLastName_firstAndLastNameMoved() throws IllegalArgumentException, IllegalAccessException
	{
		// First and last name are by default not set on the User entity anymore, so set them explicitly
		String firstName = "First name";
		String lastName = "Last name";
		firstNameUserField.set(richard, firstName);
		lastNameUserField.set(richard, lastName);

		migrationStep.upgrade(richard);

		assertThat(firstNameUserPrivateField.get(userPrivateField.get(richard)), equalTo(firstName));
		assertThat(lastNameUserPrivateField.get(userPrivateField.get(richard)), equalTo(lastName));
		assertThat(firstNameUserField.get(richard), is(nullValue()));
		assertThat(lastNameUserField.get(richard), is(nullValue()));

		verify(mockMessageService, times(1)).broadcastMessageToBuddies(Matchers.<UserAnonymizedDto> any(),
				messageSupplierCaptor.capture());
		Message message = messageSupplierCaptor.getValue().get();
		assertThat(message, instanceOf(BuddyInfoChangeMessage.class));
		BuddyInfoChangeMessage buddyInfoChangeMessage = (BuddyInfoChangeMessage) message;
		assertThat(buddyInfoChangeMessage.getNewFirstName(), equalTo(firstName));
		assertThat(buddyInfoChangeMessage.getNewLastName(), equalTo(lastName));
		assertThat(buddyInfoChangeMessage.getNewNickname(), equalTo(richard.getNickname()));
		assertThat(buddyInfoChangeMessage.getMessage(), equalTo("User changed personal info"));
	}

	@Test
	public void upgrade_moveFirstAndLastNameTwice_idempotent() throws IllegalArgumentException, IllegalAccessException
	{
		// First and last name are by default not set on the User entity anymore, so set them explicitly
		String firstName = "First name";
		String lastName = "Last name";
		firstNameUserField.set(richard, firstName);
		lastNameUserField.set(richard, lastName);

		migrationStep.upgrade(richard);
		migrationStep.upgrade(richard);

		assertThat(firstNameUserPrivateField.get(userPrivateField.get(richard)), equalTo(firstName));
		assertThat(lastNameUserPrivateField.get(userPrivateField.get(richard)), equalTo(lastName));
	}
}