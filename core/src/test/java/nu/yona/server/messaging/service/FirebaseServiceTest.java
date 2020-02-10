/*******************************************************************************
 * Copyright (c) 2018, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.repository.Repository;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;

import nu.yona.server.Translator;
import nu.yona.server.device.entities.DeviceAnonymizedRepository;
import nu.yona.server.device.entities.UserDeviceRepository;
import nu.yona.server.device.service.DeviceService;
import nu.yona.server.entities.DeviceAnonymizedRepositoryMock;
import nu.yona.server.entities.EntityWithId;
import nu.yona.server.entities.UserDeviceRepositoryMock;
import nu.yona.server.entities.UserRepositoriesConfiguration;
import nu.yona.server.exceptions.YonaException;
import nu.yona.server.messaging.entities.SystemMessage;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.test.util.BaseSpringIntegrationTest;
import nu.yona.server.test.util.JUnitUtil;
import nu.yona.server.util.AsyncExecutor;

@Configuration
@ComponentScan(useDefaultFilters = false, basePackages = { "nu.yona.server.messaging.service",
		"nu.yona.server.subscriptions.service", "nu.yona.server.properties", "nu.yona.server" }, includeFilters = {
				@ComponentScan.Filter(pattern = "nu.yona.server.messaging.service.FirebaseService", type = FilterType.REGEX),
				@ComponentScan.Filter(pattern = "nu.yona.server.properties.YonaProperties", type = FilterType.REGEX) })
class FirebaseServiceTestConfiguration extends UserRepositoriesConfiguration
{
	@Bean
	UserDeviceRepository getMockDeviceRepository()
	{
		return Mockito.spy(new UserDeviceRepositoryMock());
	}

	@Bean
	DeviceAnonymizedRepository getMockDeviceAnonymizedRepository()
	{
		return Mockito.spy(new DeviceAnonymizedRepositoryMock());
	}
}

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { FirebaseServiceTestConfiguration.class })
public class FirebaseServiceTest extends BaseSpringIntegrationTest
{
	private static final Constructor<FirebaseMessagingException> firebaseMessagingConstructor = JUnitUtil
			.getAccessibleConstructor(FirebaseMessagingException.class, String.class, String.class, Throwable.class);
	private static final Field messageIdField = JUnitUtil.getAccessibleField(EntityWithId.class, "id");

	@MockBean
	private Translator mockTranslator;

	@MockBean
	private AsyncExecutor mockAsyncExecutor;

	@MockBean
	private DeviceService mockDeviceService;

	@MockBean
	private FirebaseMessaging mockFirebaseMessaging;

	@Autowired
	private YonaProperties yonaProperties;

	@Autowired
	private FirebaseService service;

	@BeforeEach
	public void setUp()
	{
		doAnswer(invocation -> {
			exec(invocation.getArgument(1), invocation.getArgument(2));
			return null;
		}).when(mockAsyncExecutor).execAsync(any(), any(), any());

		yonaProperties.getFirebase().setEnabled(true);
	}

	@Override
	protected Map<Class<?>, Repository<?, ?>> getRepositories()
	{
		return new HashMap<>();
	}

	@Test
	public void sendMessage_success_sendsFirebaseMessage() throws FirebaseMessagingException
	{
		UUID deviceAnonymizedId = UUID.randomUUID();
		String registrationToken = "token-" + UUID.randomUUID();
		SystemMessage message = SystemMessage.createInstance("Hi there!");
		setId(message, 5); // Make it look like a saved message

		service.sendMessage(deviceAnonymizedId, registrationToken, message);

		verify(mockFirebaseMessaging, times(1)).send(any());
		verify(mockDeviceService, never()).clearFirebaseInstanceId(any());
	}

	@Test
	public void sendMessage_unregisteredError_clearsFirebaseInstanceId() throws FirebaseMessagingException
	{
		UUID deviceAnonymizedId = UUID.randomUUID();
		String registrationToken = "token-" + UUID.randomUUID();
		SystemMessage message = SystemMessage.createInstance("Hi there!");
		setId(message, 5); // Make it look like a saved message
		when(mockFirebaseMessaging.send(any()))
				.thenThrow(createFirebaseMessagingException("registration-token-not-registered", "Sorry, failed"));

		service.sendMessage(deviceAnonymizedId, registrationToken, message);

		verify(mockDeviceService, times(1)).clearFirebaseInstanceId(deviceAnonymizedId);
	}

	@Test
	public void sendMessage_unknownError_fails() throws FirebaseMessagingException
	{
		UUID deviceAnonymizedId = UUID.randomUUID();
		String registrationToken = "token-" + UUID.randomUUID();
		SystemMessage message = SystemMessage.createInstance("Hi there!");
		setId(message, 5); // Make it look like a saved message
		when(mockFirebaseMessaging.send(any()))
				.thenThrow(createFirebaseMessagingException("some-unknown-error", "Sorry, failed"));

		assertThrows(FirebaseServiceException.class, () -> service.sendMessage(deviceAnonymizedId, registrationToken, message));

		verify(mockDeviceService, never()).clearFirebaseInstanceId(any());
	}

	private FirebaseMessagingException createFirebaseMessagingException(String errorCode, String message)
	{
		try
		{
			return firebaseMessagingConstructor.newInstance(errorCode, message, null);
		}
		catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
				| SecurityException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	private void setId(SystemMessage message, int i)
	{
		try
		{
			messageIdField.set(message, i);
		}
		catch (IllegalArgumentException | IllegalAccessException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	private void exec(Runnable action, Consumer<Optional<Throwable>> completionHandler)
	{
		try
		{
			action.run();
			completionHandler.accept(Optional.empty());
		}
		catch (Throwable e)
		{
			completionHandler.accept(Optional.of(e));
			throw e;
		}
	}
}
