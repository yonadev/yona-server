/*******************************************************************************
 * Copyright (c) 2015, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.ldap.LdapAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.hateoas.UriTemplate;
import org.springframework.hateoas.config.EnableHypermediaSupport;
import org.springframework.hateoas.config.EnableHypermediaSupport.HypermediaType;
import org.springframework.hateoas.mediatype.hal.CurieProvider;
import org.springframework.hateoas.mediatype.hal.DefaultCurieProvider;
import org.springframework.hateoas.server.LinkRelationProvider;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.annotation.EnableAsync;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.exceptions.YonaException;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.rest.JsonRootLinkRelationProvider;

@EnableHypermediaSupport(type = HypermediaType.HAL)
@EnableSpringDataWebSupport
@EnableAsync
@Configuration
@EnableAutoConfiguration(exclude = { LdapAutoConfiguration.class })
public class CoreConfiguration
{
	private static final Logger logger = LoggerFactory.getLogger(CoreConfiguration.class);

	@Autowired
	private YonaProperties yonaProperties;

	@Bean
	LinkRelationProvider relProvider()
	{
		return new JsonRootLinkRelationProvider();
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
	@ConditionalOnProperty("yona.firebase.enabled")
	public FirebaseMessaging firebaseMessaging()
	{
		String fileName = yonaProperties.getFirebase().getAdminServiceAccountKeyFile();
		logger.info("Reading the Firebase service account info from {}", fileName);
		try (InputStream serviceAccount = new FileInputStream(fileName))
		{
			FirebaseOptions options = new FirebaseOptions.Builder().setCredentials(GoogleCredentials.fromStream(serviceAccount))
					.setDatabaseUrl(yonaProperties.getFirebase().getDatabaseUrl()).build();

			FirebaseApp.initializeApp(options);
			return FirebaseMessaging.getInstance();
		}
		catch (IOException e)
		{
			throw YonaException.unexpected(e);
		}
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
		return new DefaultCurieProvider("yona", UriTemplate.of("http://dev.yona.nu/rels/{rel}"));
	}
}