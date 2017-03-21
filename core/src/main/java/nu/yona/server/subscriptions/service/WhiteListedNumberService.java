/*******************************************************************************
 * Copyright (c) 2015, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import nu.yona.server.subscriptions.entities.WhiteListedNumber;

@Service
public class WhiteListedNumberService
{
	@Autowired
	private UserService userService;

	@Transactional
	public void addWhiteListedNumber(String mobileNumber)
	{
		userService.assertValidMobileNumber(mobileNumber);

		WhiteListedNumber.getRepository().save(WhiteListedNumber.createInstance(mobileNumber));
	}

	@Transactional
	public Set<String> getAllWhiteListedNumbers()
	{
		return StreamSupport.stream(WhiteListedNumber.getRepository().findAll().spliterator(), false)
				.map(whiteListedNumberEntity -> whiteListedNumberEntity.getMobileNumber()).collect(Collectors.toSet());
	}

	@Transactional
	public void assertMobileNumberIsAllowed(String mobileNumber)
	{
		if (WhiteListedNumber.getRepository().findByMobileNumber(mobileNumber) == null)
		{
			throw WhiteListedNumberServiceException.numberNotWhiteListed(mobileNumber);
		}
	}
}
