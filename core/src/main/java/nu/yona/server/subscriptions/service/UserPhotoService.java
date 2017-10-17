/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import nu.yona.server.subscriptions.entities.UserPhoto;
import nu.yona.server.subscriptions.entities.UserPhotoRepository;

@Service
public class UserPhotoService
{
	@Autowired
	private UserPhotoRepository userPhotoRepository;

	public UserPhotoDto addUserPhoto(UserPhotoDto unsavedInstance)
	{
		return UserPhotoDto.createInstance(userPhotoRepository.save(UserPhoto.createInstance(unsavedInstance.getPngBytes())));
	}

	public UserPhotoDto getUserPhoto(UUID userPhotoId)
	{
		UserPhoto userPhoto = userPhotoRepository.findOne(userPhotoId);
		if (userPhoto == null)
		{
			throw UserPhotoServiceException.notFoundById(userPhotoId);
		}
		return UserPhotoDto.createInstance(userPhoto);
	}
}
