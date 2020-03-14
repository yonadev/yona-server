/*
 * Copyright (c) 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package nu.yona.server.subscriptions.service;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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

import nu.yona.server.batch.client.BatchProxyService;
import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.entities.MessageSourceRepositoryMock;
import nu.yona.server.entities.UserRepositoriesConfiguration;
import nu.yona.server.messaging.entities.MessageSource;
import nu.yona.server.sms.SmsTemplate;
import nu.yona.server.subscriptions.entities.ConfirmationCode;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.test.util.BaseSpringIntegrationTest;
import nu.yona.server.test.util.JUnitUtil;

@Configuration
@ComponentScan(useDefaultFilters = false, basePackages = { "nu.yona.server.subscriptions.service",
		"nu.yona.server.messaging.service", "nu.yona.server.properties" }, includeFilters = {
				@ComponentScan.Filter(pattern = "nu.yona.server.subscriptions.service.PinResetRequestService", type = FilterType.REGEX),
				@ComponentScan.Filter(pattern = "nu.yona.server.properties.YonaProperties", type = FilterType.REGEX) })
class PinResetRequestServiceTestConfiguration extends UserRepositoriesConfiguration
{
}

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { PinResetRequestServiceTestConfiguration.class })
class PinResetRequestServiceTest extends BaseSpringIntegrationTest
{
	private User richard;

	@Autowired
	private PinResetRequestService service;

	@MockBean
	private UserService userService;

	@MockBean
	private BatchProxyService batchProxyService;

	@BeforeEach
	public void setUpPerTest() throws Exception
	{
		try (CryptoSession cryptoSession = CryptoSession.start(BuddyDeviceChangeMessageDtoTestConfiguration.PASSWORD))
		{
			richard = JUnitUtil.createRichard();
		}
	}

	@Test
	void sendPinResetConfirmationCode_userExists_messageSent()
	{
		String code = "9876";
		when(userService.generateConfirmationCode()).thenReturn(code);
		when(userService.getUserEntityByIdIfExisting(eq(richard.getId()))).thenReturn(Optional.of(richard));

		ConfirmationCode confirmationCode = new ConfirmationCode();
		richard.setPinResetConfirmationCode(confirmationCode);

		service.sendPinResetConfirmationCode(richard.getId());

		ArgumentCaptor<ConfirmationCode> confirmationCodeCaptor = ArgumentCaptor.forClass(ConfirmationCode.class);
		verify(userService, times(1)).sendConfirmationCodeTextMessage(eq(richard.getMobileNumber()),
				confirmationCodeCaptor.capture(), eq(SmsTemplate.PIN_RESET_REQUEST_CONFIRMATION));
		assertThat("Expected right related user set to goal conflict message", confirmationCodeCaptor.getValue().getCode(),
				equalTo(code));
	}

	@Test
	void sendPinResetConfirmationCode_userDoesNotExist_noMessageSent()
	{
		service.sendPinResetConfirmationCode(UUID.randomUUID());

		verify(userService, never()).sendConfirmationCodeTextMessage(any(), any(), any());
	}

	@Override
	protected Map<Class<?>, Repository<?, ?>> getRepositories()
	{
		Map<Class<?>, Repository<?, ?>> repositoriesMap = new HashMap<>();
		repositoriesMap.put(MessageSource.class, new MessageSourceRepositoryMock());
		return repositoriesMap;
	}
}