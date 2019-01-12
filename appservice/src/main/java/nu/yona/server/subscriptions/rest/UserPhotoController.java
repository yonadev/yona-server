/*******************************************************************************
 * Copyright (c) 2017, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.rest;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.ExposesResourceFor;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;
import org.springframework.hateoas.mvc.ResourceAssemblerSupport;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartFile;

import nu.yona.server.crypto.seckey.CryptoSession;
import nu.yona.server.exceptions.InvalidDataException;
import nu.yona.server.exceptions.YonaException;
import nu.yona.server.rest.Constants;
import nu.yona.server.rest.ControllerBase;
import nu.yona.server.rest.ErrorResponseDto;
import nu.yona.server.rest.GlobalExceptionMapping;
import nu.yona.server.subscriptions.rest.UserPhotoController.UserPhotoResource;
import nu.yona.server.subscriptions.service.UserPhotoDto;
import nu.yona.server.subscriptions.service.UserPhotoService;
import nu.yona.server.subscriptions.service.UserService;

@Controller
@ExposesResourceFor(UserPhotoResource.class)
@RequestMapping(value = "/", produces = { MediaType.APPLICATION_JSON_VALUE })
public class UserPhotoController extends ControllerBase
{
	@Autowired
	private UserService userService;

	@Autowired
	private UserPhotoService userPhotoService;

	@Autowired
	private GlobalExceptionMapping globalExceptionMapping;

	@GetMapping(value = "/userPhotos/{userPhotoId}", produces = { MediaType.IMAGE_PNG_VALUE })
	@ResponseBody
	public ResponseEntity<byte[]> getUserPhoto(@PathVariable UUID userPhotoId)
	{
		return new ResponseEntity<>(userPhotoService.getUserPhoto(userPhotoId).getPngBytes(), HttpStatus.OK);
	}

	@PutMapping(value = "/users/{userId}/photo", consumes = "multipart/form-data")
	@ResponseBody
	public ResponseEntity<UserPhotoResource> uploadUserPhoto(
			@RequestHeader(value = Constants.PASSWORD_HEADER) Optional<String> password,
			@RequestParam(value = "file", required = false) MultipartFile userPhoto, @PathVariable UUID userId)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password,
				() -> userService.doPreparationsAndCheckCanAccessPrivateData(userId)))
		{
			return createOkResponse(userPhotoService.addUserPhoto(userId, UserPhotoDto.createInstance(getPngBytes(userPhoto))),
					new UserPhotoResourceAssembler());
		}
	}

	@ExceptionHandler(MultipartException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	@ResponseBody
	public ErrorResponseDto handleMultipartException(Exception exception, HttpServletRequest request)
	{
		return globalExceptionMapping.handleOtherException(exception, request);
	}

	@DeleteMapping(value = "/users/{userId}/photo")
	@ResponseStatus(HttpStatus.OK)
	public void removeUserPhoto(@RequestHeader(value = Constants.PASSWORD_HEADER) Optional<String> password,
			@PathVariable UUID userId)
	{
		try (CryptoSession cryptoSession = CryptoSession.start(password,
				() -> userService.doPreparationsAndCheckCanAccessPrivateData(userId)))
		{
			userPhotoService.removeUserPhoto(userId);
		}
	}

	private static byte[] getPngBytes(MultipartFile file)
	{
		try (InputStream inStream = file.getInputStream())
		{
			BufferedImage image = ImageIO.read(inStream);
			if (image == null)
			{
				throw InvalidDataException.unsupportedPhotoFileType();
			}
			ByteArrayOutputStream pngBytes = new ByteArrayOutputStream();
			ImageIO.write(image, "png", pngBytes);
			return pngBytes.toByteArray();
		}
		catch (IOException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	public static ControllerLinkBuilder getUserPhotoLinkBuilder(UUID userPhotoId)
	{
		return linkTo(methodOn(UserPhotoController.class).getUserPhoto(userPhotoId));
	}

	public static class UserPhotoResource extends Resource<UserPhotoDto>
	{
		public UserPhotoResource(UserPhotoDto userPhoto)
		{
			super(userPhoto);
		}
	}

	public static class UserPhotoResourceAssembler extends ResourceAssemblerSupport<UserPhotoDto, UserPhotoResource>
	{
		public UserPhotoResourceAssembler()
		{
			super(UserPhotoController.class, UserPhotoResource.class);
		}

		@Override
		public UserPhotoResource toResource(UserPhotoDto userPhoto)
		{
			UserPhotoResource userPhotoResource = instantiateResource(userPhoto);
			addPhotoLink(userPhotoResource);
			return userPhotoResource;
		}

		@Override
		protected UserPhotoResource instantiateResource(UserPhotoDto userPhoto)
		{
			return new UserPhotoResource(userPhoto);
		}

		private void addPhotoLink(Resource<UserPhotoDto> userPhotoResource)
		{
			userPhotoResource.add(UserPhotoController.getUserPhotoLinkBuilder(userPhotoResource.getContent().getId().get())
					.withRel("userPhoto"));
		}
	}
}
