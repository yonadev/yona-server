/*******************************************************************************
 * Copyright (c) 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.batch.service;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;

import com.fasterxml.jackson.annotation.JsonRootName;

@JsonRootName("batchJobResult")
public class BatchJobResultDto
{
	private final Map<String, Integer> writeCountPerStep;

	public BatchJobResultDto(Map<String, Integer> writeCountPerStep)
	{
		this.writeCountPerStep = writeCountPerStep;
	}

	public Map<String, Integer> getWriteCountPerStep()
	{
		return writeCountPerStep;
	}

	static BatchJobResultDto createInstance(JobExecution jobExecution)
	{
		return new BatchJobResultDto(jobExecution.getStepExecutions().stream()
				.collect(Collectors.toMap(StepExecution::getStepName, e -> (int) e.getWriteCount())));
	}
}
