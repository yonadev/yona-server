/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.annotation.PostConstruct;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring4.SpringTemplateEngine;
import org.thymeleaf.templateresolver.FileTemplateResolver;

import nu.yona.server.exceptions.YonaException;
import nu.yona.server.properties.PropertyInitializer;
import nu.yona.server.properties.YonaProperties;

@SpringBootApplication
@EnableCaching
public class AppServiceApplication
{
	@Autowired
	private YonaProperties yonaProperties;

	public static void main(String[] args)
	{
		PropertyInitializer.initializePropertiesFromEnvironment();
		SpringApplication.run(AppServiceApplication.class, args);
	}

	@PostConstruct
	public void initialize()
	{
		Security.addProvider(new BouncyCastleProvider());
	}

	@Bean
	public WebMvcConfigurer corsConfigurer()
	{
		return new WebMvcConfigurerAdapter() {
			@Override
			public void addCorsMappings(CorsRegistry registry)
			{
				registry.addMapping("/swagger/swagger-spec.yaml");
				if (yonaProperties.getSecurity().isCorsAllowed())
				{
					// Enable CORS for the other resources, to allow testing the API through Swagger UI.
					registry.addMapping("/**").allowedMethods("GET", "HEAD", "POST", "PUT", "DELETE");
				}
			}
		};
	}

	@Bean
	@Qualifier("sslRootCertificate")
	public X509Certificate sslRootCertificate()
	{
		String sslRootCertFile = yonaProperties.getSecurity().getSslRootCertFile();
		try (InputStream inStream = new FileInputStream(sslRootCertFile))
		{
			return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(inStream);
		}
		catch (IOException | CertificateException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	@Bean
	@Qualifier("appleMobileConfigTemplateEngine")
	public TemplateEngine appleMobileConfigTemplateEngine()
	{
		String appleMobileConfigFile = yonaProperties.getAppleMobileConfig().getAppleMobileConfigFile();
		FileTemplateResolver templateResolver = new FileTemplateResolver();
		templateResolver.setPrefix(new File(appleMobileConfigFile).getParent() + File.separator);
		templateResolver.setSuffix(appleMobileConfigFile.substring(appleMobileConfigFile.lastIndexOf('.')));

		TemplateEngine templateEngine = new SpringTemplateEngine();
		templateEngine.setTemplateResolver(templateResolver);
		return templateEngine;
	}
}