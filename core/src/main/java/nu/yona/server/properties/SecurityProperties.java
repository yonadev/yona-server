/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.properties;

import java.time.Duration;

public class SecurityProperties
{
	private int confirmationCodeDigits = 4;
	private int confirmationCodeMaxAttempts = 5;
	private Duration newDeviceRequestExpirationTime = Duration.ofDays(1);
	private Duration pinResetRequestExpirationTime = Duration.ofDays(7);
	private Duration pinResetRequestConfirmationCodeDelay = Duration.ofSeconds(10);
	private int passwordLength = 32;
	private Duration dosProtectionWindow = Duration.ofMinutes(5);
	private boolean isDosProtectionEnabled = false;
	private int maxCreateUserAttemptsPerTimeWindow = 1;
	private int maxUpdateUserAttemptsPerTimeWindow = 1;
	private String sslRootCertFile;
	private String ovpnProfileFile;
	private String firebaseAdminServiceAccountKeyFile;

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

	public Duration getNewDeviceRequestExpirationTime()
	{
		return newDeviceRequestExpirationTime;
	}

	public void setNewDeviceRequestExpirationTime(String newDeviceRequestExpiration)
	{
		this.newDeviceRequestExpirationTime = Duration.parse(newDeviceRequestExpiration);
	}

	public Duration getPinResetRequestExpirationTime()
	{
		return pinResetRequestExpirationTime;
	}

	public void setPinResetRequestExpirationTime(String pinResetRequestExpiration)
	{
		this.pinResetRequestExpirationTime = Duration.parse(pinResetRequestExpiration);
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

	public void setDosProtectionWindow(String dosProtectionWindow)
	{
		this.dosProtectionWindow = Duration.parse(dosProtectionWindow);
	}

	public Duration getDosProtectionWindow()
	{
		return dosProtectionWindow;
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

	public int getMaxUpdateUserAttemptsPerTimeWindow()
	{
		return maxUpdateUserAttemptsPerTimeWindow;
	}

	public void setMaxUpdateUserAttemptsPerTimeWindow(int maxUpdateUserAttemptsPerTimeWindow)
	{
		this.maxUpdateUserAttemptsPerTimeWindow = maxUpdateUserAttemptsPerTimeWindow;
	}

	public String getSslRootCertFile()
	{
		return this.sslRootCertFile;
	}

	public void setSslRootCertFile(String sslRootCertFile)
	{
		this.sslRootCertFile = sslRootCertFile;
	}

	public String getOvpnProfileFile()
	{
		return ovpnProfileFile;
	}

	public void setOvpnProfileFile(String ovpnProfileFile)
	{
		this.ovpnProfileFile = ovpnProfileFile;
	}

	public String getFirebaseAdminServiceAccountKeyFile()
	{
		return firebaseAdminServiceAccountKeyFile;
	}

	public void setFirebaseAdminServiceAccountKeyFile(String firebaseAdminServiceAccountKeyFile)
	{
		this.firebaseAdminServiceAccountKeyFile = firebaseAdminServiceAccountKeyFile;
	}
}
