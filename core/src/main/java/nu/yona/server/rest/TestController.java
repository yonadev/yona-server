/*******************************************************************************
 * Copyright (c) 2018, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.rest;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;

import nu.yona.server.email.EmailService;
import nu.yona.server.email.EmailService.EmailDto;
import nu.yona.server.exceptions.YonaException;
import nu.yona.server.messaging.service.FirebaseService;

/**
 * This controller is only created for integration tests. A service like the email service can be configured for testing. In that
 * case, it doesn't send email but stores the last one in a field. This controller fetches that field.
 */
@Controller
@RequestMapping(value = "/test", produces = { MediaType.APPLICATION_JSON_VALUE })
public class TestController extends ControllerBase
{
	@Autowired
	private EmailService emailService;

	@Autowired
	private FirebaseService firebaseService;

	/**
	 * Returns the last e-mail that was prepared to be sent (but not sent because e-mail was disabled).
	 * 
	 * @return the last e-mail that was prepared to be sent (but not sent because e-mail was disabled).
	 */
	@GetMapping(value = "/emails/last")
	@ResponseBody
	public HttpEntity<EmailResource> getLastEMail()
	{
		return createOkResponse(emailService.getLastEmail(), createEMailResourceAssembler());
	}

	private EmailResourceAssembler createEMailResourceAssembler()
	{
		return new EmailResourceAssembler();
	}

	static class EmailResource extends EntityModel<EmailDto>
	{
		public EmailResource(EmailDto email)
		{
			super(email);
		}

	}

	static class EmailResourceAssembler extends RepresentationModelAssemblerSupport<EmailDto, EmailResource>
	{
		public EmailResourceAssembler()
		{
			super(TestController.class, EmailResource.class);
		}

		@Override
		public EmailResource toModel(EmailDto email)
		{
			return instantiateModel(email);
		}

		@Override
		protected EmailResource instantiateModel(EmailDto email)
		{
			return new EmailResource(email);
		}
	}

	/**
	 * Returns the last Firebase message that was prepared to be sent (but not sent because Firebase was disabled).
	 * 
	 * @param registrationToken the registration token for which the message must be retrieved
	 * @return the last Firebase message that was prepared to be sent (but not sent because Firebase was disabled).
	 */
	@GetMapping(value = "/firebase/messages/last/{registrationToken}")
	@ResponseBody
	public HttpEntity<FirebaseMessageResource> getLastFirebaseMessage(@PathVariable String registrationToken)
	{
		Optional<Message> lastMessage = firebaseService.getLastMessage(registrationToken);
		if (lastMessage.isEmpty())
		{
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
		return createOkResponse(FirebaseMessageDto.createInstance(lastMessage.get()), createFirebaseMessageResourceAssembler());
	}

	/**
	 * Clears the last Firebase message that was prepared to be sent (but not sent because Firebase was disabled).
	 * 
	 * @param registrationToken the registration token for which the message must be retrieved
	 * @return the status
	 */
	@DeleteMapping(value = "/firebase/messages/last/{registrationToken}")
	@ResponseBody
	public HttpEntity<FirebaseMessageResource> clearLastFirebaseMessage(@PathVariable String registrationToken)
	{
		Optional<Message> lastMessage = firebaseService.clearLastMessage(registrationToken);
		if (lastMessage.isEmpty())
		{
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
		return createOkResponse(FirebaseMessageDto.createInstance(lastMessage.get()), createFirebaseMessageResourceAssembler());
	}

	private FirebaseMessageResourceAssembler createFirebaseMessageResourceAssembler()
	{
		return new FirebaseMessageResourceAssembler();
	}

	public static class FirebaseMessageDto
	{
		private static final Field notificationField = getFieldAndSetAccessible(Message.class, "notification");
		private static final Field dataField = getFieldAndSetAccessible(Message.class, "data");
		private static final Field titleField = getFieldAndSetAccessible(Notification.class, "title");
		private static final Field bodyField = getFieldAndSetAccessible(Notification.class, "body");
		private final String title;
		private final String body;
		private final Map<String, String> data;

		public FirebaseMessageDto(String title, String body, Map<String, String> data)
		{
			this.title = title;
			this.body = body;
			this.data = data;
		}

		@SuppressWarnings("unchecked")
		public static FirebaseMessageDto createInstance(Message message)
		{
			try
			{
				Notification notification = (Notification) notificationField.get(message);
				Map<String, String> data = (Map<String, String>) dataField.get(message);
				String title = (String) titleField.get(notification);
				String body = (String) bodyField.get(notification);
				return new FirebaseMessageDto(title, body, data);
			}
			catch (Exception e)
			{
				throw YonaException.unexpected(e);
			}
		}

		private static Field getFieldAndSetAccessible(Class<?> clazz, String fieldName)
		{
			Field field = Arrays.asList(clazz.getDeclaredFields()).stream().filter(f -> f.getName().equals(fieldName)).findAny()
					.orElseThrow(() -> new IllegalStateException("Cannot find field '" + fieldName + "'"));
			field.setAccessible(true);
			return field;
		}

		public String getTitle()
		{
			return title;
		}

		public String getBody()
		{
			return body;
		}

		public Map<String, String> getData()
		{
			return data;
		}
	}

	static class FirebaseMessageResource extends EntityModel<FirebaseMessageDto>
	{
		public FirebaseMessageResource(FirebaseMessageDto email)
		{
			super(email);
		}

	}

	static class FirebaseMessageResourceAssembler extends RepresentationModelAssemblerSupport<FirebaseMessageDto, FirebaseMessageResource>
	{
		public FirebaseMessageResourceAssembler()
		{
			super(TestController.class, FirebaseMessageResource.class);
		}

		@Override
		public FirebaseMessageResource toModel(FirebaseMessageDto email)
		{
			return instantiateModel(email);
		}

		@Override
		protected FirebaseMessageResource instantiateModel(FirebaseMessageDto email)
		{
			return new FirebaseMessageResource(email);
		}
	}
}
