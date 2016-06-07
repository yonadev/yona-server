/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.properties;

import java.time.Duration;

public class BatchProperties
{
	private Duration pinResetRequestConfirmationCodeInterval = Duration.parse("PT10S");

	public Duration getPinResetRequestConfirmationCodeInterval()
	{
		return pinResetRequestConfirmationCodeInterval;
	}

	public void setPinResetRequestConfirmationCodeInterval(String pinResetRequestConfirmationCodeInterval)
	{
		this.pinResetRequestConfirmationCodeInterval = Duration.parse(pinResetRequestConfirmationCodeInterval);
	}

}
