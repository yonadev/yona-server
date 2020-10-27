/*******************************************************************************
 * Copyright (c) 2017, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.entities;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.lang.NotImplementedException;

import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.entities.UserRepository;

public class UserRepositoryMock extends MockJpaRepositoryEntityWithUuid<User> implements UserRepository
{

	@Override
	public Optional<User> findByIdForUpdate(UUID id)
	{
		return findById(id);
	}

	@Override
	public User findByMobileNumber(String mobileNumber)
	{
		return findOne(u -> u.getMobileNumber().equals(mobileNumber)).orElse(null);
	}

	@Override
	public int countByAppLastOpenedDateBetween(LocalDate startDate, LocalDate endDate)
	{
		throw new NotImplementedException();
	}

	@Override
	public int countByAppLastOpenedDateIsNull()
	{
		throw new NotImplementedException();
	}

	@Override
	public int countByMobileNumberConfirmationCodeIsNull()
	{
		throw new NotImplementedException();
	}

	@Override
	public int countByMobileNumberConfirmationCodeIsNotNull()
	{
		throw new NotImplementedException();
	}

	@Override
	public int countByMobileNumberConfirmationCodeIsNotNullAndIsCreatedOnBuddyRequest(boolean isCreatedOnBuddyRequest)
	{
		throw new NotImplementedException();
	}

	@Override
	public Set<User> findAllWithExpiredNewDeviceRequests(LocalDateTime cuttOffDate)
	{
		throw new NotImplementedException();
	}

	@Override
	public int countByNumberOfDaysAppOpenedAfterInstallation(int minNumberOfDays, int maxNumberOfDays)
	{
		throw new NotImplementedException();
	}
}