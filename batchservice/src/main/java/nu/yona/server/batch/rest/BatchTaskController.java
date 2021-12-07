/*******************************************************************************
 * Copyright (c) 2017, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.batch.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import nu.yona.server.batch.client.PinResetConfirmationCodeSendRequestDto;
import nu.yona.server.batch.client.SystemMessageSendRequestDto;
import nu.yona.server.batch.service.BatchJobResultDto;
import nu.yona.server.batch.service.BatchTaskService;
import nu.yona.server.exceptions.InvalidDataException;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.util.Require;

@Controller
@RequestMapping(value = "/batch", produces = { MediaType.APPLICATION_JSON_VALUE })
public class BatchTaskController
{
	@Autowired
	private YonaProperties yonaProperties;

	@Autowired
	private BatchTaskService batchTaskService;

	@PostMapping(value = "/sendPinResetConfirmationCode/")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void requestPinResetConfirmationCode(
			@RequestBody PinResetConfirmationCodeSendRequestDto pinResetConfirmationCodeSendRequest)
	{
		batchTaskService.requestPinResetConfirmationCode(pinResetConfirmationCodeSendRequest);
	}

	@PostMapping(value = "/sendSystemMessage/")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void sendSystemMessage(@RequestBody SystemMessageSendRequestDto systemMessageSendRequest)
	{
		batchTaskService.sendSystemMessage(systemMessageSendRequest);
	}

	// NOTICE: For integration test purposes. It executes the job synchronously.
	@PostMapping(value = "/aggregateActivities/")
	public HttpEntity<EntityModel<BatchJobResultDto>> aggregateActivities()
	{
		Require.that(yonaProperties.isTestServer(),
				() -> InvalidDataException.onlyAllowedOnTestServers("Endpoint /batch/aggregateActivities/ is not available"));
		return new ResponseEntity<>(EntityModel.of(batchTaskService.aggregateActivities()), HttpStatus.OK);
	}
}
