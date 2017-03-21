package nu.yona.server.batch.quartz;

import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.quartz.impl.matchers.GroupMatcher;
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

	public Set<JobDto> getAllJobs()
	{
		return getJobGroupNames().stream().flatMap(gn -> getJobKeys(gn).stream()).map(jk -> getJobDetail(jk))
				.map(jd -> JobDto.createInstance(jd)).collect(Collectors.toSet());
	}

	public Set<JobDto> getJobsInGroup(String group)
	{
		return getJobKeys(group).stream().map(jk -> getJobDetail(jk)).map(jd -> JobDto.createInstance(jd))
				.collect(Collectors.toSet());
	}

	public JobDto getJob(String name, String group)
	{
		try
		{
			return JobDto.createInstance(scheduler.getJobDetail(JobKey.jobKey(name, group)));
		}
		catch (SchedulerException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	public JobDto addJob(String group, JobDto job)
	{
		try
		{
			scheduler.addJob(JobBuilder.newJob(job.getJobClass()).storeDurably().requestRecovery()
					.withDescription(job.getDescription()).withIdentity(job.getName(), group).build(), false);
			return job;
		}
		catch (SchedulerException e)
		{
			throw YonaException.unexpected(e);
		}
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
