package nu.yona.server;
/*******************************************************************************
 * Copyright (c) 2015, 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/

import java.io.IOException;
import java.util.Properties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
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
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.XmlClientConfigBuilder;
import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.spring.cache.HazelcastCacheManager;

import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.exceptions.YonaException;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.rest.JsonRootRelProvider;
import nu.yona.server.rest.RestClientErrorHandler;

@EnableHypermediaSupport(type = HypermediaType.HAL)
@EnableSpringDataWebSupport
@Configuration
public class CoreConfiguration extends CachingConfigurerSupport
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
	public RestTemplate restTemplate(ObjectMapper objectMapper)
	{
		RestTemplate restTemplate = new RestTemplate();
		restTemplate.setErrorHandler(new RestClientErrorHandler(objectMapper));
		return restTemplate;
	}

	@Override
	@Bean
	public CacheManager cacheManager()
	{
		return new HazelcastCacheManager(hazelcastInstance());
	}

	@Bean
	public HazelcastInstance hazelcastInstance()
	{
		String hazelcastConfigFilePath = yonaProperties.getHazelcastConfigFilePath();
		if (hazelcastConfigFilePath == null)
		{
			return Hazelcast.newHazelcastInstance(new Config());
		}
		return getHazelcastClientInstance(hazelcastConfigFilePath);
	}

	private HazelcastInstance getHazelcastClientInstance(String hazelcastConfigFilePath)
	{
		try
		{
			return HazelcastClient.newHazelcastClient(new XmlClientConfigBuilder(hazelcastConfigFilePath).build());
		}
		catch (IOException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	@Bean
	public CacheManager localCache()
	{
		return new ConcurrentMapCacheManager();
	}
}
