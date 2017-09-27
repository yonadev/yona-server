/*******************************************************************************
 * Copyright (c) 2015, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.rest;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import nu.yona.server.subscriptions.service.UserPhotoService;

@Controller
@RequestMapping(value = "/userPhotos", produces = { MediaType.IMAGE_PNG_VALUE })
public class UserPhotoController
{
	@Autowired
	private UserPhotoService userPhotoService;

	@RequestMapping(value = "/{userPhotoId}", method = RequestMethod.GET)
	@ResponseBody
	public byte[] getUserPhoto(@PathVariable UUID userPhotoId)
	{
		return userPhotoService.getUserPhoto(userPhotoId).getPngBytes();
	}
}