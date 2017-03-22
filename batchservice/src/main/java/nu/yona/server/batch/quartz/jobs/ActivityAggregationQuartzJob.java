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
