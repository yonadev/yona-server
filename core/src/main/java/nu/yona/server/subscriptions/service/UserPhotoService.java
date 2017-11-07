/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import nu.yona.server.subscriptions.entities.UserPhoto;
import nu.yona.server.subscriptions.entities.UserPhotoRepository;

@Service
public class UserPhotoService
{
	@Autowired
	private UserService userService;

	@Autowired
	private UserPhotoRepository userPhotoRepository;

	@Transactional
	public UserPhotoDto addUserPhoto(UUID userId, UserPhotoDto unsavedInstance)
	{
		UserPhoto userPhoto = userPhotoRepository.save(UserPhoto.createInstance(unsavedInstance.getPngBytes()));
		userService.updateUserPhoto(userId, Optional.of(userPhoto.getId()));
		return UserPhotoDto.createInstance(userPhoto);
	}

	@Transactional
	public UserPhotoDto getUserPhoto(UUID userPhotoId)
	{
		UserPhoto userPhoto = userPhotoRepository.findOne(userPhotoId);
		if (userPhoto == null)
		{
			throw UserPhotoServiceException.notFoundById(userPhotoId);
		}
		return UserPhotoDto.createInstance(userPhoto);
	}

	@Transactional
	public void removeUserPhoto(UUID userId)
	{
		userService.updateUserPhoto(userId, Optional.empty());
	}
}
