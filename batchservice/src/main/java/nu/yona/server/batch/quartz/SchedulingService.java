/*******************************************************************************
 * Copyright (c) 2017 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.batch.quartz;

import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;

import java.util.Date;
import java.util.Map;

import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import nu.yona.server.exceptions.YonaException;

@Service
public class SchedulingService
{
	public enum ScheduleGroup
	{
		CLEAN_UP, GENERATE, OTHER
	}

	private static final Logger logger = LoggerFactory.getLogger(SchedulingService.class);

	@Autowired
	private Scheduler scheduler;

	public void schedule(ScheduleGroup group, String jobName, String triggerName, Map<?, ?> jobData, Date date)
	{
		try
		{
			JobDetail job = scheduler.getJobDetail(JobKey.jobKey(jobName, group.toString()));
			TriggerKey triggerKey = TriggerKey.triggerKey(triggerName, group.toString());
			if (scheduler.checkExists(triggerKey))
			{
				logger.warn("Trigger with key {} already exists, so skipping new trigger", triggerKey);
				return;
			}
			Trigger trigger = newTrigger().forJob(job).withIdentity(triggerKey).usingJobData(new JobDataMap(jobData))
					.startAt(date).withSchedule(simpleSchedule().withMisfireHandlingInstructionFireNow()).build();
			scheduler.scheduleJob(trigger);
		}
		catch (SchedulerException e)
		{
			throw YonaException.unexpected(e);
		}
	}
}
