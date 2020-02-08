/*******************************************************************************
 * Copyright (c) 2018, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.rest;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import nu.yona.server.exceptions.InvalidDataException;
import nu.yona.server.exceptions.YonaException;
import nu.yona.server.messaging.service.FirebaseService;
import nu.yona.server.messaging.service.FirebaseService.MessageData;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.util.Require;

/**
 * This controller is only created for integration tests. A service like the email service can be configured for testing. In that
 * case, it doesn't send email but stores the last one in a field. This controller fetches that field.
 */
@Controller
@RequestMapping(value = "/test", produces = { MediaType.APPLICATION_JSON_VALUE })
public class TestController extends ControllerBase
{
	private static final Logger logger = LoggerFactory.getLogger(TestController.class);

	@Autowired
	private YonaProperties yonaProperties;

	@Autowired
	private EmailService emailService;

	@Autowired
	private FirebaseService firebaseService;

	@Autowired
	private PassThroughHeadersHolder headersHolder;

	private final CyclicBarrier passThroughHeadersRequestBarrier = new CyclicBarrier(2);

	/**
	 * Returns the last e-mail that was prepared to be sent (but not sent because e-mail was disabled).
	 * 
	 * @return the last e-mail that was prepared to be sent (but not sent because e-mail was disabled).
	 */
	@GetMapping(value = "/emails/last")
	@ResponseBody
	public HttpEntity<EmailResource> getLastEMail()
	{
		Require.that(yonaProperties.isTestServer(),
				() -> InvalidDataException.onlyAllowedOnTestServers("Endpoint /emails/last is not available"));
		return createOkResponse(emailService.getLastEmail(), createEMailResourceAssembler());
	}

	private EmailResourceAssembler createEMailResourceAssembler()
	{
		return new EmailResourceAssembler();
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
		Require.that(yonaProperties.isTestServer(),
				() -> InvalidDataException.onlyAllowedOnTestServers("Endpoint /firebase/messages/last/ is not available"));
		Optional<MessageData> lastMessage = firebaseService.getLastMessage(registrationToken);
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
		Require.that(yonaProperties.isTestServer(),
				() -> InvalidDataException.onlyAllowedOnTestServers("Endpoint /firebase/messages/last/ is not available"));
		Optional<MessageData> lastMessage = firebaseService.clearLastMessage(registrationToken);
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

	/**
	 * Returns the headers stored in the {@link PassThroughHeadersHolder}. This method blocks till a second request is done, thus
	 * enforcing multithreading.
	 * 
	 * @return the headers stored in the {@link PassThroughHeadersHolder}
	 */
	@GetMapping(value = "/passThroughHeaders")
	@ResponseBody
	public HttpEntity<PassThroughHeadersResource> getPassThroughHeaders()
	{
		Require.that(yonaProperties.isTestServer(),
				() -> InvalidDataException.onlyAllowedOnTestServers("Endpoint /passThroughHeaders is not available"));
		passBarrier();
		PassThroughHeadersDto passThroughHeaders = PassThroughHeadersDto.createInstance(headersHolder.export());
		return createOkResponse(passThroughHeaders, createPassThroughHeadersResourceAssembler());
	}

	private void passBarrier()
	{
		try
		{
			logger.info("GET on /passThroughHeaders: Going to wait for barrier. Current number waiting: {}",
					passThroughHeadersRequestBarrier.getNumberWaiting());
			passThroughHeadersRequestBarrier.await(30, TimeUnit.SECONDS); // If it takes more than 30 seconds before the next
																			// request arrives, something is wrong in the test
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			throw YonaException.unexpected(e);
		}
		catch (BrokenBarrierException | TimeoutException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	private PassThroughHeadersResourceAssembler createPassThroughHeadersResourceAssembler()
	{
		return new PassThroughHeadersResourceAssembler();
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

	public static class FirebaseMessageDto
	{
		private static final Field notificationField = getFieldAndSetAccessible(Message.class, "notification");
		private static final Field dataField = getFieldAndSetAccessible(Message.class, "data");
		private static final Field titleField = getFieldAndSetAccessible(Notification.class, "title");
		private static final Field bodyField = getFieldAndSetAccessible(Notification.class, "body");
		private final String title;
		private final String body;
		private final Map<String, String> data;
		private final String appOs;
		private final int appVersionCode;
		private final String appVersionName;

		public FirebaseMessageDto(String title, String body, Map<String, String> data, String appOs, int appVersionCode,
				String appVersionName)
		{
			this.title = title;
			this.body = body;
			this.data = data;
			this.appOs = appOs;
			this.appVersionCode = appVersionCode;
			this.appVersionName = appVersionName;
		}

		@SuppressWarnings("unchecked")
		public static FirebaseMessageDto createInstance(MessageData messageData)
		{
			try
			{
				Notification notification = (Notification) notificationField.get(messageData.firebaseMessage);
				Map<String, String> data = (Map<String, String>) dataField.get(messageData.firebaseMessage);
				String title = (String) titleField.get(notification);
				String body = (String) bodyField.get(notification);
				String appOs = getStringValueFromMdc(messageData, RestConstants.APP_OS_MDC_KEY).orElse(null);
				int appVersionCode = getIntValueFromMdc(messageData, RestConstants.APP_VERSION_CODE_MDC_KEY);
				String appVersionName = getStringValueFromMdc(messageData, RestConstants.APP_VERSION_NAME_MDC_KEY).orElse(null);

				return new FirebaseMessageDto(title, body, data, appOs, appVersionCode, appVersionName);
			}
			catch (IllegalArgumentException | IllegalAccessException e)
			{
				throw YonaException.unexpected(e);
			}
		}

		private static Optional<String> getStringValueFromMdc(MessageData messageData, String key)
		{
			return messageData.mdc.map(mdc -> mdc.get(key));
		}

		private static Integer getIntValueFromMdc(MessageData messageData, String key)
		{
			return getStringValueFromMdc(messageData, key).map(Integer::parseInt).orElse(0);
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

		public String getAppOs()
		{
			return appOs;
		}

		public int getAppVersionCode()
		{
			return appVersionCode;
		}

		public String getAppVersionName()
		{
			return appVersionName;
		}
	}

	static class FirebaseMessageResource extends EntityModel<FirebaseMessageDto>
	{
		public FirebaseMessageResource(FirebaseMessageDto email)
		{
			super(email);
		}

	}

	static class FirebaseMessageResourceAssembler
			extends RepresentationModelAssemblerSupport<FirebaseMessageDto, FirebaseMessageResource>
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

	public static class PassThroughHeadersDto
	{
		private final Map<String, String> passThroughHeaders;

		private PassThroughHeadersDto(Map<String, String> passThroughHeaders)
		{
			this.passThroughHeaders = passThroughHeaders;
		}

		static PassThroughHeadersDto createInstance(Map<String, String> passThroughHeaders)
		{
			return new PassThroughHeadersDto(passThroughHeaders);
		}

		public Map<String, String> getPassThroughHeaders()
		{
			return passThroughHeaders;
		}
	}

	static class PassThroughHeadersResource extends EntityModel<PassThroughHeadersDto>
	{
		public PassThroughHeadersResource(PassThroughHeadersDto passThroughHeaders)
		{
			super(passThroughHeaders);
		}

	}

	static class PassThroughHeadersResourceAssembler
			extends RepresentationModelAssemblerSupport<PassThroughHeadersDto, PassThroughHeadersResource>
	{
		public PassThroughHeadersResourceAssembler()
		{
			super(TestController.class, PassThroughHeadersResource.class);
		}

		@Override
		public PassThroughHeadersResource toModel(PassThroughHeadersDto email)
		{
			return instantiateModel(email);
		}

		@Override
		protected PassThroughHeadersResource instantiateModel(PassThroughHeadersDto email)
		{
			return new PassThroughHeadersResource(email);
		}
	}
}