/*******************************************************************************
 * Copyright (c) 2017, 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.batch.quartz.jobs;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import com.google.common.collect.ImmutableMap;

import nu.yona.server.rest.RestUtil;
import nu.yona.server.subscriptions.service.PinResetRequestService;

@Service
public class PinResetConfirmationCodeSenderQuartzJob implements org.quartz.Job
{
	private static final String USER_ID_KEY = "userId";
	private static final String LOCALE_STRING_KEY = "localeString";

	@Autowired
	private PinResetRequestService pinResetRequestService;

	@Override
	public void execute(JobExecutionContext context)
	{
		JobDataMap jobData = context.getMergedJobDataMap();
		LocaleContextHolder.setLocale(getLocale(jobData));
		pinResetRequestService.sendPinResetConfirmationCode(getUserId(jobData));
	}

	public static Map<String, Object> buildParameterMap(UUID userId, String localeString)
	{
		return ImmutableMap.of(USER_ID_KEY, userId.toString(), LOCALE_STRING_KEY, localeString);
	}

	private static UUID getUserId(Map<String, Object> parameterMap)
	{
		return RestUtil.parseUuid((String) parameterMap.get(USER_ID_KEY));
	}

	private static Locale getLocale(Map<String, Object> parameterMap)
	{
		return Locale.forLanguageTag((String) parameterMap.get(LOCALE_STRING_KEY));
	}
}
