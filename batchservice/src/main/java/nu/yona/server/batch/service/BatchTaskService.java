package nu.yona.server.batch.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.ImmutableMap;

import nu.yona.server.batch.client.PinResetConfirmationCodeSendRequestDto;
import nu.yona.server.batch.quartz.SchedulingService;
import nu.yona.server.batch.quartz.SchedulingService.ScheduleGroup;
import nu.yona.server.batch.quartz.jobs.PinResetConfirmationCodeSenderQuartzJob;
import nu.yona.server.util.TimeUtil;

@Service
public class BatchTaskService
{
	private static final String JOB_NAME = "PinResetConfirmationCode";

	private static final Logger logger = LoggerFactory.getLogger(BatchTaskService.class);

	@Autowired
	private SchedulingService schedulingService;

	public void requestPinResetConfirmationCode(PinResetConfirmationCodeSendRequestDto request)
	{
		logger.info("Received request to generate PIN reset confirmation code for user with ID {} at {}", request.getUserId(),
				request.getExecutionTime());
		schedulingService.schedule(ScheduleGroup.OTHER, JOB_NAME, JOB_NAME + " " + request.getUserId(),
				ImmutableMap.of(PinResetConfirmationCodeSenderQuartzJob.USER_ID_KEY, request.getUserId().toString(),
						PinResetConfirmationCodeSenderQuartzJob.LOCALE_STRING_KEY, request.getLocaleString()),
				TimeUtil.toDate(request.getExecutionTime()));
	}
}
