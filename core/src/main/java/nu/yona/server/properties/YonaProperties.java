/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.properties;

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
}
