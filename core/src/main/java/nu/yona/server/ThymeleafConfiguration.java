package nu.yona.server;
/*******************************************************************************
 * Copyright (c) 2015, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring4.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.AbstractTemplateResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

@Configuration
public class ThymeleafConfiguration
{
	private static final String TEMPLATES_BASE_FOLDER = "/templates/";
	private static final String SMS_TEMPLATES_FOLDER = TEMPLATES_BASE_FOLDER + "sms/";
	private static final String EMAIL_TEMPLATES_FOLDER = TEMPLATES_BASE_FOLDER + "email/";
	private static final String OTHER_TEMPLATES_FOLDER = TEMPLATES_BASE_FOLDER + "other/";
	private static final String EMAIL_TEMPLATE_ENCODING = StandardCharsets.UTF_8.name();

	@Bean
	public ResourceBundleMessageSource smsMessageSource()
	{
		final ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
		messageSource.setBasename("templates.sms.messages");
		return messageSource;
	}

	@Bean
	public ResourceBundleMessageSource emailMessageSource()
	{
		final ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
		messageSource.setBasename("templates.email.messages");
		return messageSource;
	}

	@Bean
	public TemplateEngine smsTemplateEngine()
	{
		return templateEngine(smsMessageSource(), smsTemplateResolver());
	}

	@Bean
	public TemplateEngine emailTemplateEngine()
	{
		return templateEngine(emailMessageSource(), emailHtmlTemplateResolver(), emailTextTemplateResolver());
	}

	@Bean
	public TemplateEngine otherTemplateEngine()
	{
		return templateEngine(otherJsonTemplateResolver(), otherTextTemplateResolver(), otherHtmlTemplateResolver(),
				otherXmlTemplateResolver());
	}

	private TemplateEngine templateEngine(ResourceBundleMessageSource messageSource,
			AbstractTemplateResolver... templateResolvers)
	{
		return templateEngine(Optional.of(messageSource), templateResolvers);
	}

	private TemplateEngine templateEngine(AbstractTemplateResolver... templateResolvers)
	{
		return templateEngine(Optional.empty(), templateResolvers);
	}

	private TemplateEngine templateEngine(Optional<ResourceBundleMessageSource> messageSource,
			AbstractTemplateResolver... templateResolvers)
	{
		final SpringTemplateEngine templateEngine = new SpringTemplateEngine();

		int i = 1;
		for (AbstractTemplateResolver templateResolver : templateResolvers)
		{
			templateResolver.setOrder(i++);
			templateEngine.addTemplateResolver(templateResolver);
		}
		messageSource.ifPresent(templateEngine::setTemplateEngineMessageSource);
		return templateEngine;
	}

	private AbstractTemplateResolver smsTemplateResolver()
	{
		return templateResolver("smsTemplateResolver", SMS_TEMPLATES_FOLDER, ".txt", TemplateMode.TEXT);
	}

	private AbstractTemplateResolver emailHtmlTemplateResolver()
	{
		return templateResolver("emailHtmlTemplateResolver", EMAIL_TEMPLATES_FOLDER, ".html", TemplateMode.HTML);
	}

	private AbstractTemplateResolver emailTextTemplateResolver()
	{
		return templateResolver("emailTextTemplateResolver", EMAIL_TEMPLATES_FOLDER, ".txt", TemplateMode.TEXT);
	}

	private AbstractTemplateResolver otherJsonTemplateResolver()
	{
		return templateResolver("otherJsonTemplateResolver", OTHER_TEMPLATES_FOLDER, ".json", TemplateMode.JAVASCRIPT);
	}

	private AbstractTemplateResolver otherTextTemplateResolver()
	{
		return templateResolver("otherTextTemplateResolver", OTHER_TEMPLATES_FOLDER, ".txt", TemplateMode.TEXT);
	}

	private AbstractTemplateResolver otherHtmlTemplateResolver()
	{
		return templateResolver("otherHtmlTemplateResolver", OTHER_TEMPLATES_FOLDER, ".html", TemplateMode.HTML);
	}

	private AbstractTemplateResolver otherXmlTemplateResolver()
	{
		return templateResolver("otherXmlTemplateResolver", OTHER_TEMPLATES_FOLDER, ".xml", TemplateMode.XML);
	}

	private AbstractTemplateResolver templateResolver(String name, String prefix, String suffix, TemplateMode templateMode)
	{
		ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
		templateResolver.setName(name);
		templateResolver.setResolvablePatterns(Collections.singleton("*" + suffix));
		templateResolver.setPrefix(prefix);
		templateResolver.setTemplateMode(templateMode);
		templateResolver.setCharacterEncoding(EMAIL_TEMPLATE_ENCODING);
		templateResolver.setCacheable(true);
		return templateResolver;
	}
}
