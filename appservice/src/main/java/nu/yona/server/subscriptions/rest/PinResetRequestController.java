/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.rest;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import nu.yona.server.crypto.CryptoSession;
import nu.yona.server.exceptions.ConfirmationException;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.rest.Constants;
import nu.yona.server.rest.ErrorResponseDTO;
import nu.yona.server.rest.GlobalExceptionMapping;
import nu.yona.server.subscriptions.entities.ConfirmationCode;
import nu.yona.server.subscriptions.rest.UserController.UserResource;
import nu.yona.server.subscriptions.service.ConfirmationFailedResponseDTO;
import nu.yona.server.subscriptions.service.PinResetRequestService;
import nu.yona.server.subscriptions.service.UserService;

@Controller
@RequestMapping(value = "/users/{id}/pinResetRequest", produces = { MediaType.APPLICATION_JSON_VALUE })
public class PinResetRequestController
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

	@RequestMapping(value = "/request", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<ConfirmationCodeDelayDTO> requestPinReset(
			@RequestHeader(value = Constants.PASSWORD_HEADER) Optional<String> password, @PathVariable UUID id)
	{
		CryptoSession.execute(password, () -> userService.canAccessPrivateData(id), () -> {
			pinResetRequestService.requestPinReset(id);
			return null;
		});
		return new ResponseEntity<ConfirmationCodeDelayDTO>(
				new ConfirmationCodeDelayDTO(yonaProperties.getSecurity().getPinResetRequestConfirmationCodeDelay()),
				HttpStatus.OK);
	}

	@RequestMapping(value = "/verify", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<Void> verifyPinResetConfirmationCode(
			@RequestHeader(value = Constants.PASSWORD_HEADER) Optional<String> password, @PathVariable UUID id,
			@RequestBody ConfirmationCodeDTO confirmationCode)
	{
		CryptoSession.execute(password, () -> userService.canAccessPrivateData(id), () -> {
			pinResetRequestService.verifyPinResetConfirmationCode(id, confirmationCode.getCode());
			return null;
		});
		return new ResponseEntity<Void>(HttpStatus.OK);
	}

	@RequestMapping(value = "/resend", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<Void> resendPinResetConfirmationCode(
			@RequestHeader(value = Constants.PASSWORD_HEADER) Optional<String> password, @PathVariable UUID id)
	{
		CryptoSession.execute(password, () -> userService.canAccessPrivateData(id), () -> {
			pinResetRequestService.resendPinResetConfirmationCode(id);
			return null;
		});
		return new ResponseEntity<Void>(HttpStatus.OK);
	}

	@RequestMapping(value = "/clear", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<Void> clearPinResetRequest(@RequestHeader(value = Constants.PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID id)
	{
		CryptoSession.execute(password, () -> userService.canAccessPrivateData(id), () -> {
			pinResetRequestService.clearPinResetRequest(id);
			return null;
		});
		return new ResponseEntity<Void>(HttpStatus.OK);
	}

	@ExceptionHandler(ConfirmationException.class)
	private ResponseEntity<ErrorResponseDTO> handleException(ConfirmationException e)
	{
		if (e.getRemainingAttempts() >= 0)
		{
			ErrorResponseDTO responseMessage = new ConfirmationFailedResponseDTO(e.getMessageId(), e.getMessage(),
					e.getRemainingAttempts());
			logger.error("Pin reset confirmation failed", e);
			return new ResponseEntity<ErrorResponseDTO>(responseMessage, e.getStatusCode());
		}
		return globalExceptionMapping.handleYonaException(e);
	}

	public void addLinks(UserResource userResource)
	{
		ConfirmationCode confirmationCode = userService.getUserEntityById(userResource.getContent().getId())
				.getPinResetConfirmationCode();
		if (confirmationCode == null || pinResetRequestService.isExpired(confirmationCode))
		{
			addPinResetRequestLink(userResource);
		}
		else if (confirmationCode.getConfirmationCode() != null)
		{
			addVerifyPinResetLink(userResource);
			addResendPinResetConfirmationCodeLink(userResource);
			addClearPinResetLink(userResource);
		}
	}

	private void addPinResetRequestLink(UserResource userResource)
	{
		PinResetRequestController methodOn = methodOn(PinResetRequestController.class);
		ResponseEntity<ConfirmationCodeDelayDTO> method = methodOn.requestPinReset(null, userResource.getContent().getId());
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

	public class ConfirmationCodeDelayDTO
	{
		private final Duration delay;

		public ConfirmationCodeDelayDTO(Duration delay)
		{
			this.delay = delay;
		}

		public Duration getDelay()
		{
			return delay;
		}
	}
}
