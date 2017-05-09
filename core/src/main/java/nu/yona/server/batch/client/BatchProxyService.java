/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.batch.client;

import java.time.LocalDateTime;
import java.util.UUID;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import nu.yona.server.properties.YonaProperties;

@Service
public class BatchProxyService
{
	@Autowired
	private YonaProperties yonaProperties;

	@Autowired
	private RestTemplate restTemplate;

	private String batchServiceUrl;

	@PostConstruct
	private void init()
	{
		batchServiceUrl = yonaProperties.getBatchService().getServiceUrl();
	}

	public void requestPinResetConfirmationCode(UUID userId, LocalDateTime executionTime)
	{
		restTemplate.postForEntity(buildBaseUrl() + "/sendPinResetConfirmationCode/",
				new PinResetConfirmationCodeSendRequestDto(userId, executionTime), String.class);
	}

	private String buildBaseUrl()
	{
		return batchServiceUrl + "/batch";
	}
}
