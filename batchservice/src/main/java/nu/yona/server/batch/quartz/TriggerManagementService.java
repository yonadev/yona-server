/*******************************************************************************
 * Copyright (c) 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.batch.quartz;

import static java.util.stream.Collectors.toSet;
import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.TriggerBuilder.newTrigger;

import java.util.List;
import java.util.Set;
import java.util.TimeZone;

import javax.transaction.Transactional;

import org.quartz.CronTrigger;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerKey;
import org.quartz.impl.matchers.GroupMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import nu.yona.server.exceptions.YonaException;

@Service
public class TriggerManagementService
{
	private enum ChangeType
	{
		ADD, UPDATE
	}

	private static final Logger logger = LoggerFactory.getLogger(TriggerManagementService.class);

	@Autowired
	private Scheduler scheduler;

	public Set<CronTriggerDto> getAllTriggers()
	{
		return getTriggerGroupNames().stream().flatMap(gn -> getTriggerKeys(gn).stream()).map(this::getTrigger)
				.filter(t -> t instanceof CronTrigger).map(CronTriggerDto::createInstance).collect(toSet());
	}

	public Set<CronTriggerDto> getTriggersInGroup(String group)
	{
		return getTriggerKeys(group).stream().map(this::getTrigger).filter(t -> t instanceof CronTrigger)
				.map(CronTriggerDto::createInstance).collect(toSet());
	}

	public CronTriggerDto getTrigger(String name, String group)
	{
		return CronTriggerDto.createInstance(getTrigger(TriggerKey.triggerKey(name, group)));
	}

	@Transactional
	public CronTriggerDto addTrigger(String group, CronTriggerDto trigger)
	{
		logger.info("Adding trigger '{}' to group '{}'", trigger.getName(), group);
		return addOrUpdateTrigger(group, trigger, ChangeType.ADD);
	}

	@Transactional
	public CronTriggerDto updateTrigger(String group, CronTriggerDto trigger)
	{
		logger.info("Updating trigger '{}' in group '{}'", trigger.getName(), group);
		return addOrUpdateTrigger(group, trigger, ChangeType.UPDATE);
	}

	@Transactional
	public void deleteTrigger(String group, String name)
	{
		try
		{
			logger.info("Deleting trigger '{}' from group '{}'", name, group);
			scheduler.unscheduleJob(TriggerKey.triggerKey(name, group));
		}
		catch (SchedulerException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	@Transactional
	public Set<CronTriggerDto> updateTriggerGroup(String group, Set<CronTriggerDto> triggers)
	{
		try
		{
			Set<String> trgNamesToBe = triggers.stream().map(CronTriggerDto::getName).collect(toSet());
			Set<TriggerKey> existingTrgs = scheduler.getTriggerKeys(GroupMatcher.triggerGroupEquals(group));
			Set<TriggerKey> keysOfTrgsToDelete = existingTrgs.stream().filter(tk -> !contains(trgNamesToBe, tk)).collect(toSet());
			Set<CronTriggerDto> trgsToUpdate = triggers.stream().filter(t -> contains(existingTrgs, group, t)).collect(toSet());
			Set<CronTriggerDto> trgsToAdd = triggers.stream().filter(t -> !contains(existingTrgs, group, t)).collect(toSet());

			keysOfTrgsToDelete.forEach(tk -> deleteTrigger(tk.getGroup(), tk.getName()));
			trgsToUpdate.forEach(t -> updateTrigger(group, t));
			trgsToAdd.forEach(t -> addTrigger(group, t));

			return getTriggersInGroup(group);
		}
		catch (SchedulerException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	private CronTriggerDto addOrUpdateTrigger(String group, CronTriggerDto triggerDto, ChangeType changeType)
	{
		try
		{
			CronTrigger trigger = createTrigger(group, triggerDto);
			if (changeType == ChangeType.ADD)
			{
				scheduler.scheduleJob(trigger);
			}
			else
			{
				scheduler.rescheduleJob(getTriggerKey(group, triggerDto), trigger);
			}
			logger.info("Next fire time for trigger '{}' in group '{}': {}", triggerDto.getName(), group,
					trigger.getNextFireTime());
			return CronTriggerDto.createInstance(trigger);
		}
		catch (SchedulerException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	private CronTrigger createTrigger(String group, CronTriggerDto triggerDto)
	{
		return newTrigger().forJob(triggerDto.getJobName(), group).withIdentity(triggerDto.getName(), group).startNow()
				.withSchedule(
						cronSchedule(triggerDto.getCronExpression()).inTimeZone(TimeZone.getTimeZone(triggerDto.getTimeZone()))
								.withMisfireHandlingInstructionFireAndProceed())
				.build();
	}

	private TriggerKey getTriggerKey(String group, CronTriggerDto trigger)
	{
		return TriggerKey.triggerKey(trigger.getName(), group);
	}

	private boolean contains(Set<String> triggerNamesToBe, TriggerKey triggerKey)
	{
		return triggerNamesToBe.contains(triggerKey.getName());
	}

	private boolean contains(Set<TriggerKey> existingTriggers, String group, CronTriggerDto trigger)
	{
		return existingTriggers.contains(getTriggerKey(group, trigger));
	}

	private List<String> getTriggerGroupNames()
	{
		try
		{
			return scheduler.getTriggerGroupNames();
		}
		catch (SchedulerException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	private CronTrigger getTrigger(TriggerKey tk)
	{
		try
		{
			return (CronTrigger) scheduler.getTrigger(tk);
		}
		catch (SchedulerException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	private Set<TriggerKey> getTriggerKeys(String gn)
	{
		try
		{
			return scheduler.getTriggerKeys(GroupMatcher.triggerGroupEquals(gn));
		}
		catch (SchedulerException e)
		{
			throw YonaException.unexpected(e);
		}
	}
}
