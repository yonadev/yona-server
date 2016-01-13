package nu.yona.server;
/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.hateoas.RelProvider;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.rest.JsonRootRelProvider;

@Configuration
public class CoreConfiguration
{
	private static final Logger logger = LoggerFactory.getLogger(CoreConfiguration.class);

	@Autowired
	private YonaProperties yonaProperties;

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
	 * This bean tells the application which message bundle to use.
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
		if (!yonaProperties.getLdap().isEnabled())
		{
			logger.info("Skipping LDAP initialization, as it's not enabled.");
			return null;
		}
		LdapContextSource contextSource = new LdapContextSource();
		contextSource.setUrl(yonaProperties.getLdap().getURL());
		contextSource.setBase(yonaProperties.getLdap().getBaseDN());
		contextSource.setUserDn(yonaProperties.getLdap().getAccessUserDN());
		contextSource.setPassword(yonaProperties.getLdap().getAccessUserPassword());
		contextSource.afterPropertiesSet();
		return new LdapTemplate(contextSource);
	}

	@Bean
	public JavaMailSender javaMailSender()
	{
		JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
		Properties mailProperties = new Properties();
		mailProperties.put("mail.smtp.auth", yonaProperties.getEmail().getSmtp().isEnableAuth());
		mailProperties.put("mail.smtp.starttls.enable", yonaProperties.getEmail().getSmtp().isEnableStartTls());
		mailSender.setJavaMailProperties(mailProperties);
		mailSender.setHost(yonaProperties.getEmail().getSmtp().getHost());
		mailSender.setPort(yonaProperties.getEmail().getSmtp().getPort());
		mailSender.setProtocol(yonaProperties.getEmail().getSmtp().getProtocol());
		mailSender.setUsername(yonaProperties.getEmail().getSmtp().getUsername());
		mailSender.setPassword(yonaProperties.getEmail().getSmtp().getPassword());
		return mailSender;
	}
}
