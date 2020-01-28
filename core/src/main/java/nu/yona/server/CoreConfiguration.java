/*******************************************************************************
 * Copyright (c) 2015, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server;

import java.util.Properties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.CustomScopeConfigurer;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.ldap.LdapAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.context.support.SimpleThreadScope;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.hateoas.RelProvider;
import org.springframework.hateoas.UriTemplate;
import org.springframework.hateoas.config.EnableHypermediaSupport;
import org.springframework.hateoas.config.EnableHypermediaSupport.HypermediaType;
import org.springframework.hateoas.hal.CurieProvider;
import org.springframework.hateoas.hal.DefaultCurieProvider;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.annotation.EnableAsync;

import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.rest.JsonRootRelProvider;

@EnableHypermediaSupport(type = HypermediaType.HAL)
@EnableSpringDataWebSupport
@EnableAsync
@Configuration
@EnableAutoConfiguration(exclude = { LdapAutoConfiguration.class })
public class CoreConfiguration
{
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

		messageBundle.setFallbackToSystemLocale(false);
		messageBundle.setBasename("classpath:messages/messages");
		messageBundle.setDefaultEncoding("UTF-8");

		return messageBundle;
	}

	@Bean
	@ConditionalOnProperty("yona.ldap.enabled")
	public LdapTemplate ldapTemplate()
	{
		LdapContextSource contextSource = new LdapContextSource();
		contextSource.setUrl(yonaProperties.getLdap().getUrl());
		contextSource.setBase(yonaProperties.getLdap().getBaseDn());
		contextSource.setUserDn(yonaProperties.getLdap().getAccessUserDn());
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

	@Bean
	public CurieProvider curieProvider()
	{
		return new DefaultCurieProvider("yona", new UriTemplate("http://dev.yona.nu/rels/{rel}"));
	}

	@Bean
	public static CustomScopeConfigurer customScopeConfigurer()
	{
		CustomScopeConfigurer configurer = new CustomScopeConfigurer();
		configurer.addScope(ThreadScope.class.getAnnotation(Scope.class).value(), new SimpleThreadScope());
		return configurer;
	}
}