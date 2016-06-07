/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.properties;

import java.time.Duration;

public class AnalysisServiceProperties
{
	private Duration conflictInterval = Duration.ofMinutes(15);
	private Duration updateSkipWindow = Duration.ofSeconds(5);
	private int daysActivityMemory = 40;
	private int weeksActivityMemory = 70;

	public int getDaysActivityMemory()
	{
		return daysActivityMemory;
	}

	public void setDaysActivityMemory(int daysActivityMemory)
	{
		this.daysActivityMemory = daysActivityMemory;
	}

	public int getWeeksActivityMemory()
	{
		return weeksActivityMemory;
	}

	public void setWeeksActivityMemory(int weeksActivityMemory)
	{
		this.weeksActivityMemory = weeksActivityMemory;
	}

	public Duration getConflictInterval()
	{
		return conflictInterval;
	}

	public void setConflictInterval(String conflictInterval)
	{
		this.conflictInterval = Duration.parse(conflictInterval);
	}

	public Duration getUpdateSkipWindow()
	{
		return updateSkipWindow;
	}

	public void setUpdateSkipWindow(String updateSkipWindow)
	{
		this.updateSkipWindow = Duration.parse(updateSkipWindow);
	}
}
