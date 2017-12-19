/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.util.Set;
import java.util.UUID;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import nu.yona.server.properties.YonaProperties;

@Service
public class AnalysisEngineProxyService
{
	@Autowired
	private YonaProperties yonaProperties;

	@Autowired
	private RestTemplate restTemplate;

	private String analysisEngineUrl;

	@PostConstruct
	private void init()
	{
		analysisEngineUrl = yonaProperties.getAnalysisService().getServiceUrl();
	}

	public void createInactivityEntities(UUID userAnonymizedId, Set<IntervalInactivityDto> intervalInactivities)
	{
		restTemplate.postForEntity(buildBaseUrl(userAnonymizedId) + "/inactivity/", intervalInactivities, String.class);
	}

	public void analyzeAppActivity(UUID userAnonymizedId, UUID deviceId, AppActivityDto appActivities)
	{
		restTemplate.postForEntity(buildBaseUrl(userAnonymizedId, deviceId) + "/appActivity/", appActivities, String.class);
	}

	private String buildBaseUrl(UUID userAnonymizedId)
	{
		return analysisEngineUrl + "/userAnonymized/" + userAnonymizedId;
	}

	private String buildBaseUrl(UUID userAnonymizedId, UUID deviceId)
	{
		return analysisEngineUrl + "/userAnonymized/" + userAnonymizedId + "/" + deviceId;
	}
}
