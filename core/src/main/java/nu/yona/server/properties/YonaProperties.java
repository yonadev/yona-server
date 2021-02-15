/*******************************************************************************
 * Copyright (c) 2016, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.properties;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.stereotype.Component;

@ConfigurationProperties("yona")
@Component
public class YonaProperties
{
	@NestedConfigurationProperty
	private final AnalysisServiceProperties analysisService = new AnalysisServiceProperties();

	@NestedConfigurationProperty
	private final EmailProperties email = new EmailProperties();

	@NestedConfigurationProperty
	private final LdapProperties ldap = new LdapProperties();

	@NestedConfigurationProperty
	private final SecurityProperties security = new SecurityProperties();

	@NestedConfigurationProperty
	private final SmsProperties sms = new SmsProperties();

	@NestedConfigurationProperty
	private final BatchServiceProperties batchService = new BatchServiceProperties();

	@NestedConfigurationProperty
	private final AppleMobileConfigProperties appleMobileConfig = new AppleMobileConfigProperties();

	@NestedConfigurationProperty
	private final FirebaseProperties firebase = new FirebaseProperties();

	private final Set<Locale> supportedLocales = new HashSet<>();

	private Set<Integer> supportedCountryCodes = new HashSet<>();

	private Locale defaultLocale;

	private String appleAppId;

	private String hazelcastConfigFilePath;

	private int maxUsers;

	private boolean isWhiteListActiveFreeSignUp;

	private boolean isWhiteListActiveInvitedUsers;

	private boolean isEnableHibernateStatsAllowed;

	private boolean isTestServer;

	private Duration overwriteUserConfirmationCodeNonResendInterval = Duration.ofSeconds(30);

	public AnalysisServiceProperties getAnalysisService()
	{
		return analysisService;
	}

	public EmailProperties getEmail()
	{
		return email;
	}

	public LdapProperties getLdap()
	{
		return ldap;
	}

	public SecurityProperties getSecurity()
	{
		return security;
	}

	public SmsProperties getSms()
	{
		return sms;
	}

	public BatchServiceProperties getBatchService()
	{
		return batchService;
	}

	public AppleMobileConfigProperties getAppleMobileConfig()
	{
		return appleMobileConfig;
	}

	public FirebaseProperties getFirebase()
	{
		return firebase;
	}

	public void setDefaultLocale(String defaultLocale)
	{
		this.defaultLocale = Locale.forLanguageTag(defaultLocale);
	}

	public Locale getDefaultLocale()
	{
		return defaultLocale;
	}

	public void setSupportedLocales(String supportedLocales)
	{
		this.supportedLocales.addAll(Arrays.asList(supportedLocales.split(",")).stream().map(Locale::forLanguageTag)
				.collect(Collectors.toSet()));
	}

	public Set<Locale> getSupportedLocales()
	{
		return Collections.unmodifiableSet(supportedLocales);
	}

	public void setSupportedCountryCodes(String supportedCountryCodes)
	{
		this.supportedCountryCodes.addAll(Arrays.asList(supportedCountryCodes.split(",")).stream().map(Integer::parseInt)
				.collect(Collectors.toSet()));
	}

	public Set<Integer> getSupportedCountryCodes()
	{
		return supportedCountryCodes;
	}

	public void setAppleAppId(String appleAppId)
	{
		this.appleAppId = appleAppId;
	}

	public String getAppleAppId()
	{
		return appleAppId;
	}

	public String getHazelcastConfigFilePath()
	{
		return hazelcastConfigFilePath;
	}

	public void setHazelcastConfigFilePath(String hazelcastConfigFilePath)
	{
		this.hazelcastConfigFilePath = hazelcastConfigFilePath;
	}

	public int getMaxUsers()
	{
		return maxUsers;
	}

	public void setMaxUsers(String maxUsersString)
	{
		this.maxUsers = Integer.parseInt(maxUsersString);
	}

	public void setWhiteListActiveFreeSignUp(boolean isWhiteListActiveFreeSignUp)
	{
		this.isWhiteListActiveFreeSignUp = isWhiteListActiveFreeSignUp;
	}

	public boolean isWhiteListActiveFreeSignUp()
	{
		return isWhiteListActiveFreeSignUp;
	}

	public void setWhiteListActiveInvitedUsers(boolean isWhiteListActiveInvitedUsers)
	{
		this.isWhiteListActiveInvitedUsers = isWhiteListActiveInvitedUsers;
	}

	public boolean isWhiteListActiveInvitedUsers()
	{
		return isWhiteListActiveInvitedUsers;
	}

	public boolean isEnableHibernateStatsAllowed()
	{
		return isEnableHibernateStatsAllowed;
	}

	public void setEnableHibernateStatsAllowed(boolean isEnableHibernateStatsAllowed)
	{
		this.isEnableHibernateStatsAllowed = isEnableHibernateStatsAllowed;
	}

	public boolean isTestServer()
	{
		return isTestServer;
	}

	public void setTestServer(boolean isTestServer)
	{
		this.isTestServer = isTestServer;
	}

	public Duration getOverwriteUserConfirmationCodeNonResendInterval()
	{
		return overwriteUserConfirmationCodeNonResendInterval;
	}

	public void setOverwriteUserConfirmationCodeNonResendInterval(Duration overwriteUserConfirmationCodeNonResendInterval)
	{
		this.overwriteUserConfirmationCodeNonResendInterval = overwriteUserConfirmationCodeNonResendInterval;
	}
}
