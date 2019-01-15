/*******************************************************************************
 * Copyright (c) 2015, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.rest;

import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import nu.yona.server.analysis.service.InactivityManagementService;
import nu.yona.server.analysis.service.IntervalInactivityDto;

@Controller
@RequestMapping(value = "/userAnonymized", produces = { MediaType.APPLICATION_JSON_VALUE })
public class InactivityManagementController
{
	@Autowired
	private InactivityManagementService inactivityManagementService;

	@PostMapping(value = "/{userAnonymizedId}/inactivity/")
	@ResponseStatus(value = HttpStatus.NO_CONTENT)
	public void createInactivityEntities(@PathVariable UUID userAnonymizedId,
			@RequestBody Set<IntervalInactivityDto> intervalInactivities)
	{
		inactivityManagementService.createInactivityEntities(userAnonymizedId, intervalInactivities);
	}
}
