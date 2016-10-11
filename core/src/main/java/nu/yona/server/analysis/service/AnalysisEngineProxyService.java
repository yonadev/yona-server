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

	private String analysisEngineURL;

	@PostConstruct
	private void init()
	{
		analysisEngineURL = yonaProperties.getAnalysisService().getServiceUrl();
	}

	public void createInactivityEntities(UUID userAnonymizedID, Set<IntervalInactivityDTO> intervalInactivities)
	{
		restTemplate.postForEntity(buildBaseURL(userAnonymizedID) + "/inactivity/", intervalInactivities, String.class);
	}

	public void analyzeAppActivity(UUID userAnonymizedID, AppActivityDTO appActivities)
	{
		restTemplate.postForEntity(buildBaseURL(userAnonymizedID) + "/appActivity/", appActivities, String.class);
	}

	private String buildBaseURL(UUID userAnonymizedID)
	{
		return analysisEngineURL + "/userAnonymized/" + userAnonymizedID;
	}
}
