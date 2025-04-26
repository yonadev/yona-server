/*******************************************************************************
 * Copyright (c) 2016, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import jakarta.annotation.Nonnull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.repository.Repository;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.entities.UserRepositoriesConfiguration;
import nu.yona.server.messaging.entities.MessageSource;
import nu.yona.server.messaging.entities.MessageSourceRepository;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.sms.SmsService;
import nu.yona.server.sms.SmsTemplate;
import nu.yona.server.subscriptions.entities.ConfirmationCode;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.test.util.BaseSpringIntegrationTest;
import nu.yona.server.test.util.JUnitUtil;
import nu.yona.server.util.HibernateHelperService;

@Configuration
@ComponentScan(useDefaultFilters = false, basePackages = { "nu.yona.server.subscriptions.service",
		"nu.yona.server.properties" }, includeFilters = {
		@ComponentScan.Filter(pattern = "nu.yona.server.subscriptions.service.User.*Service", type = FilterType.REGEX),
		@ComponentScan.Filter(pattern = "nu.yona.server.properties.YonaProperties", type = FilterType.REGEX) }, excludeFilters = {
		@ComponentScan.Filter(pattern = "nu.yona.server.subscriptions.service.UserPhotoService", type = FilterType.REGEX) })
class UserServiceTestConfiguration extends UserRepositoriesConfiguration
{
}

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { UserServiceTestConfiguration.class })
class UserServiceTest extends BaseSpringIntegrationTest
{
	private User richard;

	@MockBean
	private MessageSourceRepository mockMessageSourceRepository;

	@MockBean
	private SmsService smsService;

	@Autowired
	private YonaProperties yonaProperties;

	@MockBean
	private HibernateHelperService hibernateHelperService;

	@Autowired
	private UserService service;

	private static final String PASSWORD = "password";

	@BeforeEach
	public void setUpPerTest() throws Exception
	{
		yonaProperties.setSupportedCountryCodes("31");
		try (CryptoSession cryptoSession = CryptoSession.start(PASSWORD))
		{
			richard = JUnitUtil.createRichard();
		}
	}

	@Nonnull
	@SuppressWarnings("unchecked")
	private static <K, V> ArgumentCaptor<Map<K, V>> makeMapArgumentCaptor(Class<K> k, Class<V> v)
	{
		return ArgumentCaptor.forClass(Map.class);
	}

	@Override
	protected Map<Class<?>, Repository<?, ?>> getRepositories()
	{
		Map<Class<?>, Repository<?, ?>> repositoriesMap = new HashMap<>();
		repositoriesMap.put(MessageSource.class, mockMessageSourceRepository);
		return repositoriesMap;
	}

	@Test
	void assertValidUserFields_buddyWithAllowedFields_doesNotThrow()
	{
		UserDto user = new UserDto("John", "Doe", "john@doe.net", "+31612345678", "jd");

		service.assertValidUserFields(user, UserService.UserPurpose.BUDDY);
	}

	@Test
	void requestOverwriteUserConfirmationCode_notExisting_sent()
	{
		service.requestOverwriteUserConfirmationCode(richard.getMobileNumber());

		Optional<ConfirmationCode> overwriteUserConfirmationCode = richard.getOverwriteUserConfirmationCode();
		assertTrue(overwriteUserConfirmationCode.isPresent(), "Overwrite user confirmation code not set");

		ArgumentCaptor<Map<String, Object>> messageParamCaptor = makeMapArgumentCaptor(String.class, Object.class);
		verify(smsService, times(1)).send(eq(richard.getMobileNumber()), eq(SmsTemplate.OVERWRITE_USER_CONFIRMATION),
				messageParamCaptor.capture());
		assertThat("Correct confirmation code in message", messageParamCaptor.getValue().get("confirmationCode"),
				equalTo(overwriteUserConfirmationCode.get().getCode()));
	}

	@Test
	void requestOverwriteUserConfirmationCode_justCreated_notSentAgain()
	{
		ConfirmationCode confirmationCode = ConfirmationCode.createInstance("9876");
		richard.setOverwriteUserConfirmationCode(confirmationCode);
		Optional<ConfirmationCode> initialOverwriteUserConfirmationCode = richard.getOverwriteUserConfirmationCode();

		service.requestOverwriteUserConfirmationCode(richard.getMobileNumber());

		Optional<ConfirmationCode> overwriteUserConfirmationCode = richard.getOverwriteUserConfirmationCode();
		assertThat(overwriteUserConfirmationCode, equalTo(initialOverwriteUserConfirmationCode));

		verify(smsService, never()).send(any(), any(), any());
	}

	@Test
	void requestOverwriteUserConfirmationCode_olderExisting_sentAgain()
	{
		LocalDateTime startTime = JUnitUtil.mockCurrentTime("2020-03-19T20:47:00.000");
		ConfirmationCode confirmationCode = ConfirmationCode.createInstance("9876");
		richard.setOverwriteUserConfirmationCode(confirmationCode);
		Optional<ConfirmationCode> initialOverwriteUserConfirmationCode = richard.getOverwriteUserConfirmationCode();
		JUnitUtil.mockCurrentTime(
				startTime.plus(yonaProperties.getOverwriteUserConfirmationCodeNonResendInterval()).plusSeconds(1));

		service.requestOverwriteUserConfirmationCode(richard.getMobileNumber());

		Optional<ConfirmationCode> overwriteUserConfirmationCode = richard.getOverwriteUserConfirmationCode();
		assertTrue(overwriteUserConfirmationCode.isPresent(), "Overwrite user confirmation code not set");
		assertThat(overwriteUserConfirmationCode, not(equalTo(initialOverwriteUserConfirmationCode)));

		ArgumentCaptor<Map<String, Object>> messageParamCaptor = makeMapArgumentCaptor(String.class, Object.class);
		verify(smsService, times(1)).send(eq(richard.getMobileNumber()), eq(SmsTemplate.OVERWRITE_USER_CONFIRMATION),
				messageParamCaptor.capture());
		assertThat("Correct confirmation code in message", messageParamCaptor.getValue().get("confirmationCode"),
				equalTo(overwriteUserConfirmationCode.get().getCode()));
	}
}
