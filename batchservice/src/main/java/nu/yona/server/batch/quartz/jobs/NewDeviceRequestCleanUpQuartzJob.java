/*******************************************************************************
 * Copyright (c) 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.batch.quartz.jobs;

import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import nu.yona.server.subscriptions.service.NewDeviceRequestService;

@Service
public class NewDeviceRequestCleanUpQuartzJob implements org.quartz.Job
{
	private static final Logger logger = LoggerFactory.getLogger(NewDeviceRequestCleanUpQuartzJob.class);

	@Autowired
	private NewDeviceRequestService newDeviceRequestService;

	@Override
	public void execute(JobExecutionContext context)
	{
		logger.info("Deleting all expired new device requests");
		newDeviceRequestService.deleteAllExpiredRequests();
	}
}
