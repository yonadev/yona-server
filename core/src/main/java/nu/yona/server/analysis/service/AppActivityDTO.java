/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

/*
 * Offline activity for applications registered by the Yona app.
 * @see AnalysisEngineService
 * @see NetworkActivityDTO
 */
@JsonRootName("appActivity")
public class AppActivityDTO
{
	private String application;
	private Date startTime;
	private Date endTime;

	@JsonCreator
	public AppActivityDTO(@JsonProperty("application") String application, @JsonProperty("startTime") Date startTime,
			@JsonProperty("endTime") Date endTime)
	{
		this.application = application;
		this.startTime = startTime;
		this.endTime = endTime;
	}

	public String getApplication()
	{
		return application;
	}

	public Date getStartTime()
	{
		return startTime;
	}

	public Date getEndTime()
	{
		return endTime;
	}
}
