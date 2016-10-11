package nu.yona.server;
/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/

import java.util.Properties;

import org.apache.velocity.app.VelocityEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.properties.YonaProperties;
import nu.yona.server.rest.JsonRootRelProvider;
import nu.yona.server.rest.RestClientErrorHandler;

@EnableHypermediaSupport(type = HypermediaType.HAL)
@EnableSpringDataWebSupport
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

	private static final String SPRING_HATEOAS_OBJECT_MAPPER = "_halObjectMapper";

	@Autowired
	private BeanFactory beanFactory;

	@Bean
	ObjectMapper objectMapper()
	{
		// HATEOAS disables the default Spring configuration options described at
		// https://docs.spring.io/spring-boot/docs/current/reference/html/howto-spring-mvc.html#howto-customize-the-jackson-objectmapper
		// See https://github.com/spring-projects/spring-hateoas/issues/333.
		// We fix this by applying the Spring configurator on the HATEOAS object mapper
		// See also
		// https://github.com/spring-projects/spring-boot/blob/v1.3.2.RELEASE/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/hateoas/HypermediaAutoConfiguration.java
		// which already seems to do this but does not work
		ObjectMapper springHateoasObjectMapper = beanFactory.getBean(SPRING_HATEOAS_OBJECT_MAPPER, ObjectMapper.class);
		Jackson2ObjectMapperBuilder builder = beanFactory.getBean(Jackson2ObjectMapperBuilder.class);
		builder.configure(springHateoasObjectMapper);

		// By default, Jackson converts dates to UTC. This causes issues when passing inactivity creation requests from the app
		// service to the analysis engine service.
		springHateoasObjectMapper.disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);

		return springHateoasObjectMapper;
	}

	@Bean
	public CurieProvider curieProvider()
	{
		return new DefaultCurieProvider("yona", new UriTemplate("http://dev.yona.nu/rels/{rel}"));
	}

	@Bean
	public VelocityEngine velocityEngine()
	{
		VelocityEngine e = new VelocityEngine();
		e.init();
		return e;
	}

	@Bean
	public RestTemplate restTemplate(ObjectMapper objectMapper)
	{
		RestTemplate restTemplate = new RestTemplate();
		restTemplate.setErrorHandler(new RestClientErrorHandler(objectMapper));
		return restTemplate;
	}
}
