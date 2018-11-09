/*******************************************************************************
 * Copyright (c) 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.service;

import java.io.IOException;
import java.io.InputStream;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;

import nu.yona.server.exceptions.YonaException;
import nu.yona.server.properties.YonaProperties;

@Service
public class FirebaseService
{
	private static final Logger logger = LoggerFactory.getLogger(FirebaseService.class);

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

		try (InputStream serviceAccount = new ClassPathResource(yonaProperties.getFirebase().getAdminServiceAccountKeyFile())
				.getInputStream())
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
		if (!yonaProperties.getFirebase().isEnabled())
		{
			return;
		}

		// It is hard to add a message URL here, as only the app service is able to build it, and messages are sent from other
		// services as well
		Message firebaseMessage = Message.builder().putData("messageId", Long.toString(message.getId()))
				.setToken(registrationToken).build();

		try
		{
			FirebaseMessaging.getInstance().send(firebaseMessage);
		}
		catch (FirebaseMessagingException e)
		{
			throw FirebaseServiceException.couldNotSendMessage(e);
		}
	}
}
