/*******************************************************************************
 * Copyright (c) 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.batch.quartz;

import static java.util.stream.Collectors.toSet;

import java.util.List;
import java.util.Set;

import javax.transaction.Transactional;

import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.matchers.GroupMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import nu.yona.server.exceptions.YonaException;

@Service
public class JobManagementService
{
	private enum ChangeType
	{
		ADD, UPDATE
	}

	private static final Logger logger = LoggerFactory.getLogger(JobManagementService.class);

	@Autowired
	private Scheduler scheduler;

	public Set<JobDto> getAllJobs()
	{
		return getJobGroupNames().stream().flatMap(gn -> getJobKeys(gn).stream()).map(this::getJobDetail)
				.map(JobDto::createInstance).collect(toSet());
	}

	public Set<JobDto> getJobsInGroup(String group)
	{
		return getJobKeys(group).stream().map(this::getJobDetail).map(JobDto::createInstance).collect(toSet());
	}

	public JobDto getJob(String name, String group)
	{
		return JobDto.createInstance(getJobDetail(JobKey.jobKey(name, group)));
	}

	@Transactional
	public JobDto addJob(String group, JobDto job)
	{
		logger.info("Adding job '{}' to group '{}'", job.getName(), group);
		return addOrUpdateJob(group, job, ChangeType.ADD);
	}

	@Transactional
	public JobDto updateJob(String group, JobDto job)
	{
		logger.info("Updating job '{}' in group '{}'", job.getName(), group);
		return addOrUpdateJob(group, job, ChangeType.UPDATE);
	}

	@Transactional
	public void deleteJob(String group, String name)
	{
		try
		{
			logger.info("Deleting job '{}' from group '{}'", name, group);
			scheduler.deleteJob(JobKey.jobKey(name, group));
		}
		catch (SchedulerException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	@Transactional
	public Set<JobDto> updateJobGroup(String group, Set<JobDto> jobs)
	{
		try
		{
			Set<String> jobNamesToBe = jobs.stream().map(JobDto::getName).collect(toSet());
			Set<JobKey> existingJobs = scheduler.getJobKeys(GroupMatcher.jobGroupEquals(group));
			Set<JobKey> keysOfJobsToDelete = existingJobs.stream().filter(jk -> !contains(jobNamesToBe, jk)).collect(toSet());
			Set<JobDto> jobsToUpdate = jobs.stream().filter(j -> contains(existingJobs, group, j)).collect(toSet());
			Set<JobDto> jobsToAdd = jobs.stream().filter(j -> !contains(existingJobs, group, j)).collect(toSet());

			keysOfJobsToDelete.forEach(jk -> deleteJob(jk.getGroup(), jk.getName()));
			jobsToUpdate.forEach(j -> updateJob(group, j));
			jobsToAdd.forEach(j -> addJob(group, j));

			return getJobsInGroup(group);
		}
		catch (SchedulerException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	private JobDto addOrUpdateJob(String group, JobDto job, ChangeType changeType)
	{
		try
		{
			scheduler.addJob(JobBuilder.newJob(job.getJobClass()).storeDurably().requestRecovery()
							.withDescription(job.getDescription()).withIdentity(job.getName(), group).build(),
					changeType == ChangeType.UPDATE);
			return job;
		}
		catch (SchedulerException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	private boolean contains(Set<String> jobNamesToBe, JobKey jobKey)
	{
		return jobNamesToBe.contains(jobKey.getName());
	}

	private boolean contains(Set<JobKey> existingJobs, String group, JobDto job)
	{
		return existingJobs.contains(JobKey.jobKey(job.getName(), group));
	}

	private List<String> getJobGroupNames()
	{
		try
		{
			return scheduler.getJobGroupNames();
		}
		catch (SchedulerException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	private JobDetail getJobDetail(JobKey jk)
	{
		try
		{
			return scheduler.getJobDetail(jk);
		}
		catch (SchedulerException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	private Set<JobKey> getJobKeys(String gn)
	{
		try
		{
			return scheduler.getJobKeys(GroupMatcher.jobGroupEquals(gn));
		}
		catch (SchedulerException e)
		{
			throw YonaException.unexpected(e);
		}
	}
}
