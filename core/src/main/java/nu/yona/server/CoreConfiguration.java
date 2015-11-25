package nu.yona.server;
/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.hateoas.RelProvider;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;

import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.rest.JsonRootRelProvider;

@Configuration
public class CoreConfiguration
{
	@Bean
	RelProvider relProvider()
	{
		return new JsonRootRelProvider();
	}

	@Bean
	RepositoryProvider repositoryProvider()
	{
		return new RepositoryProvider();
	}

	/**
	 * This bean tells the application which message bunble to use.
	 * 
	 * @return The message bundle source
	 */
	@Bean(name = "messageSource")
	public ReloadableResourceBundleMessageSource messageSource()
	{
		ReloadableResourceBundleMessageSource messageBundle = new ReloadableResourceBundleMessageSource();

		messageBundle.setBasename("classpath:messages/messages");
		messageBundle.setDefaultEncoding("UTF-8");

		return messageBundle;
	}

	@Bean
	public LdapTemplate ldapTemplate()
	{
		LdapContextSource contextSource = new LdapContextSource();
		contextSource.setUrl("ldap://185.3.210.233:389");
		String base = "DC=yonadir1,DC=nu";
		contextSource.setBase(base);
		contextSource.setUserDn("CN=Manager," + base);
		contextSource.setPassword("Yona7890!");
		contextSource.afterPropertiesSet();
		return new LdapTemplate(contextSource);
	}
}
