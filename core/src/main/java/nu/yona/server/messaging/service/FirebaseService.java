/*******************************************************************************
 * Copyright (c) 2018, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.service;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;

import nu.yona.server.Constants;
import nu.yona.server.Translator;
import nu.yona.server.device.service.DeviceService;
import nu.yona.server.exceptions.YonaException;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.util.AsyncExecutor;
import nu.yona.server.util.AsyncExecutor.ThreadData;
import nu.yona.server.util.Require;

@Service
public class FirebaseService
{
	private static final String FIREBASE_ID_NOT_REGISTERED = "registration-token-not-registered";
	private static final Logger logger = LoggerFactory.getLogger(FirebaseService.class);

	private final Map<String, MessageData> lastMessageByRegistrationToken = new HashMap<>();

	@Autowired
	private Translator translator;

	@Autowired
	private YonaProperties yonaProperties;

	@Autowired
	private AsyncExecutor asyncExecutor;

	@Autowired
	private DeviceService deviceService;

	@PostConstruct
	private void init()
	{
		if (!yonaProperties.getFirebase().isEnabled())
		{
			logger.info("Firebase is disabled");
			return;
		}

		String fileName = yonaProperties.getFirebase().getAdminServiceAccountKeyFile();
		logger.info("Reading the Firebase service account info from {}", fileName);
		try (InputStream serviceAccount = new FileInputStream(fileName))
		{
			FirebaseOptions options = new FirebaseOptions.Builder().setCredentials(GoogleCredentials.fromStream(serviceAccount))
					.setDatabaseUrl(yonaProperties.getFirebase().getDatabaseUrl()).build();

			FirebaseApp.initializeApp(options);
		}
		catch (IOException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	public void sendMessage(UUID deviceAnonymizedId, String registrationToken, nu.yona.server.messaging.entities.Message message)
	{
		// The message URL might seem useful notification payload, but that is not possible as messages can be sent from anonymous
		// contexts, while the URL requires the user ID.
		String title = translator.getLocalizedMessage("notification.message.title");
		String body = translator.getLocalizedMessage("notification.message.body");
		Message firebaseMessage = Message.builder().setNotification(new Notification(title, body))
				.putData("messageId", Long.toString(getMessageId(message))).setToken(registrationToken).build();

		// Sending takes quite a bit of time, so do it asynchronously
		ThreadData threadData = asyncExecutor.getThreadData();
		asyncExecutor.execAsync(threadData, () -> sendMessage(registrationToken, firebaseMessage),
				t -> handleCompletion(t, deviceAnonymizedId, registrationToken));
	}

	private long getMessageId(nu.yona.server.messaging.entities.Message message)
	{
		Require.that(message.getId() != 0, () -> YonaException.illegalState("Message must be saved before this point"));
		return message.getId();
	}

	public Optional<MessageData> getLastMessage(String registrationToken)
	{
		return Optional.ofNullable(lastMessageByRegistrationToken.get(registrationToken));
	}

	public Optional<MessageData> clearLastMessage(String registrationToken)
	{
		return Optional.ofNullable(lastMessageByRegistrationToken.remove(registrationToken));
	}

	private void sendMessage(String registrationToken, Message firebaseMessage)
	{
		try
		{
			if (yonaProperties.getFirebase().isEnabled())
			{
				logger.info("Sending Firebase message");
				FirebaseMessaging.getInstance().send(firebaseMessage);
			}
			else
			{
				logger.info("Firebase message not sent because Firebase is disabled");
				// Store for testability
				lastMessageByRegistrationToken.put(registrationToken,
						MessageData.createInstance(MDC.getCopyOfContextMap(), firebaseMessage));
			}
		}
		catch (FirebaseMessagingException e)
		{
			throw FirebaseServiceException.couldNotSendMessage(e);
		}
	}

	private void handleCompletion(Optional<Throwable> throwable, UUID deviceAnonymizedId, String token)
	{
		throwable.ifPresentOrElse(t -> handleFailedSend(token, deviceAnonymizedId, t), this::logSuccessfulSend);
	}

	private void handleFailedSend(String token, UUID deviceAnonymizedId, Throwable throwable)
	{
		if (throwable instanceof FirebaseMessagingException
				&& FIREBASE_ID_NOT_REGISTERED.equals(((FirebaseMessagingException) throwable).getErrorCode()))
		{
			handleNotRegisteredDevice(deviceAnonymizedId);
			return;
		}
		logger.error(Constants.ALERT_MARKER, "Fatal error: Exception while sending Firebase message to '" + token + "'",
				throwable);
	}

	private void handleNotRegisteredDevice(UUID deviceAnonymizedId)
	{
		deviceService.clearFirebaseInstanceId(deviceAnonymizedId);
		logger.info("Firebase instance ID for device anonymized {} cleared, as it was not longer registered with Firebase",
				deviceAnonymizedId);
	}

	private void logSuccessfulSend()
	{
		if (yonaProperties.getFirebase().isEnabled())
		{
			logger.info("Firebase message sent successfully");
		}
	}

	public static class MessageData
	{
		public final Optional<Map<String, String>> mdc;
		public final Message firebaseMessage;

		public MessageData(Map<String, String> mdc, Message firebaseMessage)
		{
			this.mdc = Optional.ofNullable(mdc);
			this.firebaseMessage = firebaseMessage;
		}

		public static MessageData createInstance(Map<String, String> mdc, Message firebaseMessage)
		{
			return new MessageData(mdc, firebaseMessage);
		}
	}
}