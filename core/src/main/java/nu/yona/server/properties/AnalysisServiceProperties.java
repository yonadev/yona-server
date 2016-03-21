/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.properties;

public class AnalysisServiceProperties
{
	private long conflictInterval = 300000L;
	private long updateSkipWindow = 5000L;

	public long getConflictInterval()
	{
		return conflictInterval;
	}

	public void setConflictInterval(long conflictInterval)
	{
		this.conflictInterval = conflictInterval;
	}

	public long getUpdateSkipWindow()
	{
		return updateSkipWindow;
	}

	public void setUpdateSkipWindow(long updateSkipWindow)
	{
		this.updateSkipWindow = updateSkipWindow;
	}
}
