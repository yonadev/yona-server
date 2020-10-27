/*******************************************************************************
 * Copyright (c) 2016, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.rest;

import static nu.yona.server.rest.RestConstants.PASSWORD_HEADER;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;

import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.exceptions.ConfirmationException;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.rest.ControllerBase;
import nu.yona.server.rest.ErrorResponseDto;
import nu.yona.server.rest.GlobalExceptionMapping;
import nu.yona.server.subscriptions.entities.ConfirmationCode;
import nu.yona.server.subscriptions.rest.UserController.UserResource;
import nu.yona.server.subscriptions.service.ConfirmationFailedResponseDto;
import nu.yona.server.subscriptions.service.PinResetRequestService;
import nu.yona.server.subscriptions.service.UserService;

@Controller
@RequestMapping(value = "/users/{userId}/pinResetRequest", produces = { MediaType.APPLICATION_JSON_VALUE })
public class PinResetRequestController extends ControllerBase
{
	@Autowired
	private YonaProperties yonaProperties;

	private static final Logger logger = LoggerFactory.getLogger(UserController.class);

	@Autowired
	private UserService userService;

	@Autowired
	private PinResetRequestService pinResetRequestService;

	@Autowired
	private GlobalExceptionMapping globalExceptionMapping;

	@PostMapping(value = "/request")
	@ResponseBody
	public ResponseEntity<ConfirmationCodeDelayDto> requestPinReset(
			@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userId)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password,
				() -> userService.doPreparationsAndCheckCanAccessPrivateData(userId)))
		{
			pinResetRequestService.requestPinReset(userId);
			return new ResponseEntity<>(
					new ConfirmationCodeDelayDto(yonaProperties.getSecurity().getPinResetRequestConfirmationCodeDelay()),
					HttpStatus.OK);
		}
	}

	@PostMapping(value = "/verify")
	@ResponseBody
	public ResponseEntity<Void> verifyPinResetConfirmationCode(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userId, @RequestBody ConfirmationCodeDto confirmationCode)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password,
				() -> userService.doPreparationsAndCheckCanAccessPrivateData(userId)))
		{

			pinResetRequestService.verifyPinResetConfirmationCode(userId, confirmationCode.getCode());
			return createNoContentResponse();
		}
	}

	@PostMapping(value = "/resend")
	@ResponseBody
	public ResponseEntity<Void> resendPinResetConfirmationCode(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userId)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password,
				() -> userService.doPreparationsAndCheckCanAccessPrivateData(userId)))
		{
			pinResetRequestService.resendPinResetConfirmationCode(userId);
			return createNoContentResponse();
		}
	}

	@PostMapping(value = "/clear")
	@ResponseBody
	public ResponseEntity<Void> clearPinResetRequest(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userId)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password,
				() -> userService.doPreparationsAndCheckCanAccessPrivateData(userId)))
		{
			pinResetRequestService.clearPinResetRequest(userId);
			return createNoContentResponse();
		}
	}

	@ExceptionHandler(ConfirmationException.class)
	private ResponseEntity<ErrorResponseDto> handleException(ConfirmationException e, HttpServletRequest request)
	{
		if (e.getRemainingAttempts() >= 0)
		{
			ErrorResponseDto responseMessage = new ConfirmationFailedResponseDto(e.getMessageId(), e.getMessage(),
					e.getRemainingAttempts());
			logger.error("Pin reset confirmation failed", e);
			return new ResponseEntity<>(responseMessage, e.getStatusCode());
		}
		return globalExceptionMapping.handleYonaException(e, request);
	}

	public void addLinks(UserResource userResource)
	{
		Optional<ConfirmationCode> confirmationCode = userService.getUserEntityById(userResource.getContent().getId())
				.getPinResetConfirmationCode();
		if (!pinResetRequestService.isValidConfirmationCode(confirmationCode))
		{
			addPinResetRequestLink(userResource);
		}
		else if (confirmationCode.get().getCode() != null)
		{
			addVerifyPinResetLink(userResource);
			addResendPinResetConfirmationCodeLink(userResource);
			addClearPinResetLink(userResource);
		}
	}

	private void addPinResetRequestLink(UserResource userResource)
	{
		PinResetRequestController methodOn = methodOn(PinResetRequestController.class);
		ResponseEntity<ConfirmationCodeDelayDto> method = methodOn.requestPinReset(null, userResource.getContent().getId());
		addLink(userResource, method, "yona:requestPinReset");
	}

	private void addVerifyPinResetLink(UserResource userResource)
	{
		PinResetRequestController methodOn = methodOn(PinResetRequestController.class);
		ResponseEntity<Void> method = methodOn.verifyPinResetConfirmationCode(null, userResource.getContent().getId(), null);
		addLink(userResource, method, "yona:verifyPinReset");
	}

	private void addResendPinResetConfirmationCodeLink(UserResource userResource)
	{
		PinResetRequestController methodOn = methodOn(PinResetRequestController.class);
		ResponseEntity<Void> method = methodOn.resendPinResetConfirmationCode(null, userResource.getContent().getId());
		addLink(userResource, method, "yona:resendPinResetConfirmationCode");
	}

	private void addClearPinResetLink(UserResource userResource)
	{
		PinResetRequestController methodOn = methodOn(PinResetRequestController.class);
		ResponseEntity<Void> method = methodOn.clearPinResetRequest(null, userResource.getContent().getId());
		addLink(userResource, method, "yona:clearPinReset");
	}

	private void addLink(UserResource userResource, ResponseEntity<?> method, String rel)
	{
		userResource.add(linkTo(method).withRel(rel));
	}

	public class ConfirmationCodeDelayDto
	{
		private final Duration delay;

		public ConfirmationCodeDelayDto(Duration delay)
		{
			this.delay = delay;
		}

		@JsonFormat(shape = Shape.STRING)
		public Duration getDelay()
		{
			return delay;
		}
	}
}
