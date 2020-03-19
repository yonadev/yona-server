/*******************************************************************************
 * Copyright (c) 2016, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.sms.SmsService;
import nu.yona.server.sms.SmsTemplate;
import nu.yona.server.subscriptions.entities.ConfirmationCode;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.test.util.JUnitUtil;
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

import nu.yona.server.entities.UserRepositoriesConfiguration;
import nu.yona.server.messaging.entities.MessageSource;
import nu.yona.server.messaging.entities.MessageSourceRepository;
import nu.yona.server.test.util.BaseSpringIntegrationTest;
import nu.yona.server.util.LockPool;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

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
public class UserServiceTest extends BaseSpringIntegrationTest
{
	private User richard;

	@MockBean
	private MessageSourceRepository mockMessageSourceRepository;

	@MockBean
	private SmsService smsService;

	@Autowired
	private YonaProperties yonaProperties;

	@Autowired
	private UserService service;

	@BeforeEach
	public void setUpPerTest() throws Exception
	{
		try (CryptoSession cryptoSession = CryptoSession.start(BuddyDeviceChangeMessageDtoTestConfiguration.PASSWORD))
		{
			richard = JUnitUtil.createRichard();
		}
	}

	@Override
	protected Map<Class<?>, Repository<?, ?>> getRepositories()
	{
		Map<Class<?>, Repository<?, ?>> repositoriesMap = new HashMap<>();
		repositoriesMap.put(MessageSource.class, mockMessageSourceRepository);
		return repositoriesMap;
	}

	@Test
	public void assertValidUserFields_buddyWithAllowedFields_doesNotThrow()
	{
		UserDto user = new UserDto("John", "Doe", "john@doe.net", "+31612345678", "jd");

		service.assertValidUserFields(user, UserService.UserPurpose.BUDDY);
	}

	@Test
	public void requestOverwriteUserConfirmationCode_notExisting_sent()
	{
		service.requestOverwriteUserConfirmationCode(richard.getMobileNumber());

		Optional<ConfirmationCode> overwriteUserConfirmationCode = richard.getOverwriteUserConfirmationCode();
		assertTrue(overwriteUserConfirmationCode.isPresent(), "Overwrite user confirmation code not set");

		ArgumentCaptor<Map<String, Object>> messageParamCaptor = ArgumentCaptor.forClass(Map.class);
		verify(smsService, times(1)).send(eq(richard.getMobileNumber()), eq(SmsTemplate.OVERWRITE_USER_CONFIRMATION),
				messageParamCaptor.capture());
		assertThat("Correct confirmation code in message", messageParamCaptor.getValue().get("confirmationCode"),
				equalTo(overwriteUserConfirmationCode.get().getCode()));
	}

	@Test
	public void requestOverwriteUserConfirmationCode_justCreated_notSentAgain()
	{
		ConfirmationCode confirmationCode =  ConfirmationCode.createInstance("9876");
		richard.setOverwriteUserConfirmationCode(confirmationCode);
		Optional<ConfirmationCode> initialOverwriteUserConfirmationCode = richard.getOverwriteUserConfirmationCode();

		service.requestOverwriteUserConfirmationCode(richard.getMobileNumber());

		Optional<ConfirmationCode> overwriteUserConfirmationCode = richard.getOverwriteUserConfirmationCode();
		assertThat(overwriteUserConfirmationCode, equalTo(initialOverwriteUserConfirmationCode));

		verify(smsService, never()).send(any(), any(), any());
	}

	@Test
	public void requestOverwriteUserConfirmationCode_olderExisting_sentAgain()
	{
		LocalDateTime startTime = JUnitUtil.mockCurrentTime("2020-03-19T20:47:00.000");
		ConfirmationCode confirmationCode =  ConfirmationCode.createInstance("9876");
		richard.setOverwriteUserConfirmationCode(confirmationCode);
		Optional<ConfirmationCode> initialOverwriteUserConfirmationCode = richard.getOverwriteUserConfirmationCode();
		JUnitUtil.mockCurrentTime(startTime.plus(yonaProperties.getOverwriteUserConfirmationCodeValidityTime()).plusSeconds(1));

		service.requestOverwriteUserConfirmationCode(richard.getMobileNumber());

		Optional<ConfirmationCode> overwriteUserConfirmationCode = richard.getOverwriteUserConfirmationCode();
		assertTrue(overwriteUserConfirmationCode.isPresent(), "Overwrite user confirmation code not set");
		assertThat(overwriteUserConfirmationCode, not(equalTo(initialOverwriteUserConfirmationCode)));

		ArgumentCaptor<Map<String, Object>> messageParamCaptor = ArgumentCaptor.forClass(Map.class);
		verify(smsService, times(1)).send(eq(richard.getMobileNumber()), eq(SmsTemplate.OVERWRITE_USER_CONFIRMATION),
				messageParamCaptor.capture());
		assertThat("Correct confirmation code in message", messageParamCaptor.getValue().get("confirmationCode"),
				equalTo(overwriteUserConfirmationCode.get().getCode()));
	}
}
