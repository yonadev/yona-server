/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import nu.yona.server.properties.YonaProperties;
import nu.yona.server.subscriptions.entities.WhitelistedNumber;

@Service
public class WhitelistedNumberService
{
	@Autowired
	private UserService userService;

	@Autowired
	private YonaProperties yonaProperties;

	@CacheEvict(value = "whitelistedNumberSet", key = "'instance'")
	@Transactional
	public void addWhitelistedNumber(String mobileNumber)
	{
		userService.validateMobileNumber(mobileNumber);

		WhitelistedNumber.getRepository().save(WhitelistedNumber.createInstance(mobileNumber));
	}

	@Cacheable(value = "whitelistedNumberSet", key = "'instance'")
	@Transactional
	public Set<String> getAllWhitelistedNumbers()
	{
		return StreamSupport.stream(WhitelistedNumber.getRepository().findAll().spliterator(), false)
				.map(whitelistedNumberEntity -> whitelistedNumberEntity.getMobileNumber()).collect(Collectors.toSet());
	}

	@Transactional
	public void validateNumber(String mobileNumber)
	{
		if (!yonaProperties.getWhitelistEnabled())
			return;

		if (!getAllWhitelistedNumbers().contains(mobileNumber))
		{
			throw WhitelistedNumberServiceException.numberNotWhitelisted(mobileNumber);
		}
	}
}
