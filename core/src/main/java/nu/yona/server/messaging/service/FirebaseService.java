/*******************************************************************************
 * Copyright (c) 2018, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.service;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;

import nu.yona.server.Translator;
import nu.yona.server.exceptions.YonaException;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.util.Require;

@Service
public class FirebaseService
{
	private static final Logger logger = LoggerFactory.getLogger(FirebaseService.class);

	private final Map<String, Message> lastMessageByRegistrationToken = new HashMap<>();

	@Autowired
	private Translator translator;

	@Autowired
	private YonaProperties yonaProperties;

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

	public void sendMessage(String registrationToken, nu.yona.server.messaging.entities.Message message)
	{
		// The message URL might seem useful notification payload, but that is not possible as messages can be sent from anonymous
		// contexts, while the URL requires the user ID.
		String title = translator.getLocalizedMessage("notification.message.title");
		String body = translator.getLocalizedMessage("notification.message.body");
		Message firebaseMessage = Message.builder().setNotification(new Notification(title, body))
				.putData("messageId", Long.toString(getMessageId(message))).setToken(registrationToken).build();

		if (yonaProperties.getFirebase().isEnabled())
		{
			// Sending takes quite a bit of time, so do it asynchronously
			CompletableFuture.runAsync(() -> sendMessage(firebaseMessage))
					.whenCompleteAsync((r, t) -> logIfCompletedWithException(t));
		}
		else
		{
			// Store for testability
			lastMessageByRegistrationToken.put(registrationToken, firebaseMessage);
		}
	}

	private long getMessageId(nu.yona.server.messaging.entities.Message message)
	{
		Require.that(message.getId() != 0, () -> YonaException.illegalState("Message must be saved before this point"));
		return message.getId();
	}

	public Optional<Message> getLastMessage(String registrationToken)
	{
		return Optional.ofNullable(lastMessageByRegistrationToken.get(registrationToken));
	}

	private void sendMessage(Message firebaseMessage)
	{
		try
		{
			FirebaseMessaging.getInstance().send(firebaseMessage);
		}
		catch (FirebaseMessagingException e)
		{
			throw FirebaseServiceException.couldNotSendMessage(e);
		}
	}

	private void logIfCompletedWithException(Throwable throwable)
	{
		if (throwable != null)
		{
			logger.error("Fatal error: Exception while sending Firebase message", throwable);
		}
	}
}