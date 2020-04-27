/*******************************************************************************
 * Copyright (c) 2017, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import nu.yona.server.subscriptions.entities.User;
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
	public UserPhotoDto addUserPhoto(UUID userId, UserPhotoDto userPhoto)
	{
		User user = userService.lockUserForUpdate(userId);
		UserPhoto userPhotoEntity = userPhotoRepository.save(UserPhoto.createInstance(userPhoto.getPngBytes()));
		userService.updateUserPhoto(user, Optional.of(userPhotoEntity));
		return UserPhotoDto.createInstance(userPhotoEntity);
	}

	@Transactional
	public UserPhotoDto getUserPhoto(UUID userPhotoId)
	{
		UserPhoto userPhoto = userPhotoRepository.findById(userPhotoId)
				.orElseThrow(() -> UserPhotoServiceException.notFoundById(userPhotoId));
		return UserPhotoDto.createInstance(userPhoto);
	}

	@Transactional
	public void removeUserPhoto(UUID userId)
	{
		User user = userService.lockUserForUpdate(userId);
		userService.updateUserPhoto(user, Optional.empty());
	}
}
