/*******************************************************************************
 * Copyright (c) 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.batch.service;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.batch.core.JobExecution;

import com.fasterxml.jackson.annotation.JsonRootName;

@JsonRootName("activityAggregationBatchJobResult")
public class ActivityAggregationBatchJobResultDto
{
	private final Map<String, Integer> writeCountPerStep;

	public ActivityAggregationBatchJobResultDto(Map<String, Integer> writeCountPerStep)
	{
		this.writeCountPerStep = writeCountPerStep;
	}

	public Map<String, Integer> getWriteCountPerStep()
	{
		return writeCountPerStep;
	}

	static ActivityAggregationBatchJobResultDto createInstance(JobExecution jobExecution)
	{
		return new ActivityAggregationBatchJobResultDto(jobExecution.getStepExecutions().stream()
				.collect(Collectors.toMap(se -> se.getStepName(), se -> se.getWriteCount())));
	}
}
