/*******************************************************************************
 * Copyright (c) 2017 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.batch.quartz.jobs;

import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import nu.yona.server.batch.service.BatchTaskService;

@Service
public class ActivityAggregationQuartzJob implements org.quartz.Job
{
	@Autowired
	private BatchTaskService batchTaskService;

	@Override
	public void execute(JobExecutionContext context)
	{
		batchTaskService.aggregateActivities();
	}
}
