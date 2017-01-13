/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.properties;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties("yona")
@Configuration
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
	private final BatchProperties batch = new BatchProperties();

	private final Set<Locale> supportedLocales = new HashSet<>();

	private Locale defaultLocale;

	private String appleAppId;

	private int maxUsers;

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

	public BatchProperties getBatch()
	{
		return batch;
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
		this.supportedLocales.addAll(Arrays.asList(supportedLocales.split(",")).stream().map(ls -> Locale.forLanguageTag(ls))
				.collect(Collectors.toSet()));
	}

	public Set<Locale> getSupportedLocales()
	{
		return Collections.unmodifiableSet(supportedLocales);
	}

	public void setAppleAppId(String appleAppId)
	{
		this.appleAppId = appleAppId;
	}

	public String getAppleAppId()
	{
		return appleAppId;
	}

	public int getMaxUsers()
	{
		return maxUsers;
	}

	public void setMaxUsers(String maxUsersString)
	{
		this.maxUsers = Integer.parseInt(maxUsersString);
	}
}
