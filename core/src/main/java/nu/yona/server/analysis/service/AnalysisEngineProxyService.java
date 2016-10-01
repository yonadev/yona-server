/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import nu.yona.server.exceptions.UpstreamException;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.rest.ErrorResponseDTO;
import nu.yona.server.rest.RestUtil;

@Service
public class AnalysisEngineProxyService
{
	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private YonaProperties yonaProperties;

	@Autowired
	private RestTemplate restTemplate;

	private String analysisEngineURL;

	@PostConstruct
	private void init()
	{
		analysisEngineURL = yonaProperties.getAnalysisService().getServiceURL();
	}

	public void createInactivityEntities(UUID userAnonymizedID, Set<IntervalInactivity> intervalInactivities)
	{
		ResponseEntity<String> response = restTemplate.postForEntity(analysisEngineURL + "/inactivity/" + userAnonymizedID,
				intervalInactivities, String.class);
		if (RestUtil.isError(response.getStatusCode()))
		{
			handleError(response);
		}
	}

	public void addAppActivity(UUID userAnonymizedID, AppActivityDTO appActivities)
	{
		ResponseEntity<String> response = restTemplate
				.postForEntity(analysisEngineURL + "/analysisEngine/" + userAnonymizedID + "/", appActivities, String.class);
		if (RestUtil.isError(response.getStatusCode()))
		{
			handleError(response);
		}
	}

	private void handleError(ResponseEntity<String> response)
	{
		Optional<ErrorResponseDTO> yonaErrorResponse = getYonaErrorResponse(response);
		yonaErrorResponse.ifPresent(yer -> {
			throw UpstreamException.yonaException(response.getStatusCode(), yer.getCode(), yer.getMessage());
		});
		throw UpstreamException.analysisEngineError(response.getStatusCode(), response.getBody());
	}

	private Optional<ErrorResponseDTO> getYonaErrorResponse(ResponseEntity<String> response)
	{
		try
		{
			if (response.getStatusCode().series() == HttpStatus.Series.CLIENT_ERROR)
			{
				return Optional.of(objectMapper.readValue(response.getBody(), ErrorResponseDTO.class));
			}
		}
		catch (IOException e)
		{
			// Ignore and just return empty
		}
		return Optional.empty();
	}
}
