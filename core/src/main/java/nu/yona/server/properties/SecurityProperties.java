/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.properties;

import java.time.Duration;

public class SecurityProperties
{
	private int confirmationCodeDigits = 4;
	private int confirmationCodeMaxAttempts = 5;
	private int newDeviceRequestExpirationDays = 1;
	private int pinResetRequestExpirationDays = 7;
	private Duration pinResetRequestConfirmationCodeDelay = Duration.ZERO;
	private int passwordLength = 32;
	private long bruteForceBlockMinutes = 60;
	private long dosProtectionWindowSeconds = 300;
	private boolean isDosProtectionEnabled = false;
	private int maxCreateUserAttemptsPerTimeWindow = 1;

	/**
	 * If true, Cross Origin Resource Sharing is allowed. This is necessary for Swagger UI.
	 */
	private boolean isCorsAllowed;

	public int getConfirmationCodeDigits()
	{
		return confirmationCodeDigits;
	}

	public void setConfirmationCodeDigits(int confirmationCodeDigits)
	{
		this.confirmationCodeDigits = confirmationCodeDigits;
	}

	public int getConfirmationCodeMaxAttempts()
	{
		return confirmationCodeMaxAttempts;
	}

	public void setConfirmationMaxAttempts(int confirmationMaxAttempts)
	{
		this.confirmationCodeMaxAttempts = confirmationMaxAttempts;
	}

	public int getNewDeviceRequestExpirationDays()
	{
		return newDeviceRequestExpirationDays;
	}

	public void setNewDeviceRequestExpirationDays(int newDeviceRequestExpiration)
	{
		this.newDeviceRequestExpirationDays = newDeviceRequestExpiration;
	}

	public int getPinResetRequestExpirationDays()
	{
		return pinResetRequestExpirationDays;
	}

	public void setPinResetRequestExpirationDays(int pinResetRequestExpirationDays)
	{
		this.pinResetRequestExpirationDays = pinResetRequestExpirationDays;
	}

	public Duration getPinResetRequestConfirmationCodeDelay()
	{
		return pinResetRequestConfirmationCodeDelay;
	}

	public void setPinResetRequestConfirmationCodeDelay(String pinResetRequestConfirmationCodeDelay)
	{
		this.pinResetRequestConfirmationCodeDelay = Duration.parse(pinResetRequestConfirmationCodeDelay);
	}

	public int getPasswordLength()
	{
		return passwordLength;
	}

	public void setPasswordLength(int passwordLength)
	{
		this.passwordLength = passwordLength;
	}

	public void setBruteForceBlockMinutes(long bruteForceBlockMinutes)
	{
		this.bruteForceBlockMinutes = bruteForceBlockMinutes;
	}

	public long getBruteForceBlockMinutes()
	{
		return bruteForceBlockMinutes;
	}

	public void setDosProtectionWindowSeconds(long dosProtectionWindowSeconds)
	{
		this.dosProtectionWindowSeconds = dosProtectionWindowSeconds;
	}

	public long getDosProtectionWindowSeconds()
	{
		return dosProtectionWindowSeconds;
	}

	public void setDosProtectionEnabled(boolean isDosProtectionEnabled)
	{
		this.isDosProtectionEnabled = isDosProtectionEnabled;
	}

	public boolean isDosProtectionEnabled()
	{
		return isDosProtectionEnabled;
	}

	public int getMaxCreateUserAttemptsPerTimeWindow()
	{
		return maxCreateUserAttemptsPerTimeWindow;
	}

	public void setMaxCreateUserAttemptsPerTimeWindow(int maxCreateUserAttemptsPerTimeWindow)
	{
		this.maxCreateUserAttemptsPerTimeWindow = maxCreateUserAttemptsPerTimeWindow;
	}

	public void setCorsAllowed(boolean isCorsAllowed)
	{
		this.isCorsAllowed = isCorsAllowed;
	}

	public boolean isCorsAllowed()
	{
		return isCorsAllowed;
	}
}
