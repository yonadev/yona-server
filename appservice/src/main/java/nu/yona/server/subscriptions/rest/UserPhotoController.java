/*******************************************************************************
 * Copyright (c) 2015, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.rest;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.ExposesResourceFor;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;
import org.springframework.hateoas.mvc.ResourceAssemblerSupport;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import nu.yona.server.exceptions.InvalidDataException;
import nu.yona.server.exceptions.YonaException;
import nu.yona.server.subscriptions.rest.UserPhotoController.UserPhotoResource;
import nu.yona.server.subscriptions.service.UserPhotoDto;
import nu.yona.server.subscriptions.service.UserPhotoService;

@Controller
@ExposesResourceFor(UserPhotoResource.class)
@RequestMapping(value = "/userPhotos", produces = { MediaType.APPLICATION_JSON_VALUE })
public class UserPhotoController
{
	@Autowired
	private UserPhotoService userPhotoService;

	@RequestMapping(value = "/{userPhotoId}", method = RequestMethod.GET, produces = { MediaType.IMAGE_PNG_VALUE })
	@ResponseBody
	public ResponseEntity<byte[]> getUserPhoto(@PathVariable UUID userPhotoId)
	{
		return new ResponseEntity<>(userPhotoService.getUserPhoto(userPhotoId).getPngBytes(), HttpStatus.OK);
	}

	@RequestMapping(value = "/", method = RequestMethod.POST, consumes = "multipart/form-data")
	@ResponseBody
	public ResponseEntity<UserPhotoResource> uploadUserPhoto(@RequestParam("file") MultipartFile userPhoto)
	{
		return createUserPhotoUploadResponse(
				userPhotoService.addUserPhoto(UserPhotoDto.createUnsavedInstance(getPngBytes(userPhoto))));
	}

	private ResponseEntity<UserPhotoResource> createUserPhotoUploadResponse(UserPhotoDto userPhoto)
	{
		return new ResponseEntity<>(new UserPhotoResourceAssembler().toResource(userPhoto), HttpStatus.CREATED);
	}

	private static byte[] getPngBytes(MultipartFile file)
	{
		try
		{
			BufferedImage image = ImageIO.read(file.getInputStream());
			if (image == null)
			{
				throw InvalidDataException.notSupportedPhotoFileType();
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

	private static ControllerLinkBuilder getUserPhotoLinkBuilder(UUID userPhotoId)
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
			addSelfLink(userPhotoResource);
			return userPhotoResource;
		}

		@Override
		protected UserPhotoResource instantiateResource(UserPhotoDto userPhoto)
		{
			return new UserPhotoResource(userPhoto);
		}

		private void addSelfLink(Resource<UserPhotoDto> userPhotoResource)
		{
			userPhotoResource
					.add(UserPhotoController.getUserPhotoLinkBuilder(userPhotoResource.getContent().getId()).withSelfRel());
		}
	}
}