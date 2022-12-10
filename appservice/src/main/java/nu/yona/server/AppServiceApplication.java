/*******************************************************************************
 * Copyright (c) 2015, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Enumeration;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.validation.Validator;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring5.SpringTemplateEngine;
import org.thymeleaf.templateresolver.FileTemplateResolver;

import jakarta.annotation.PostConstruct;
import nu.yona.server.exceptions.ConfigurationException;
import nu.yona.server.exceptions.YonaException;
import nu.yona.server.properties.PropertyInitializer;
import nu.yona.server.properties.YonaProperties;

@SpringBootApplication
@EnableCaching
public class AppServiceApplication implements WebMvcConfigurer
{
	private static final Logger logger = LoggerFactory.getLogger(AppServiceApplication.class);

	private final YonaProperties yonaProperties;

	public AppServiceApplication(YonaProperties yonaProperties)
	{
		this.yonaProperties = yonaProperties;
	}

	public static void main(String[] args)
	{
		PropertyInitializer.initializePropertiesFromEnvironment();
		ConfigurableApplicationContext context = SpringApplication.run(AppServiceApplication.class, args);
		ApplicationStatusLogger.addLoggerForContextClosedEvent(context);
	}

	@PostConstruct
	public void initialize()
	{
		Security.addProvider(new BouncyCastleProvider());
	}

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

	@Bean
	public static Validator configurationPropertiesValidator()
	{
		// This bean is marked static for good reasons:
		// The configuration properties validator is created very early in the application's lifecycle and declaring the @Bean
		// method as static allows the bean to be created without having to instantiate the @Configuration class
		return new PropertiesValidator();
	}

	@Bean
	@Qualifier("appleMobileConfigSigningKeyStore")
	public KeyStore appleMobileConfigSigningKeyStore()
	{
		String fileName = yonaProperties.getAppleMobileConfig().getSigningKeyStoreFile();
		logger.info("Loading Apple mobile config signing key store from {}", fileName);
		try (InputStream inStream = new FileInputStream(fileName))
		{
			KeyStore keyStore = java.security.KeyStore.getInstance("PKCS12");
			keyStore.load(inStream, yonaProperties.getAppleMobileConfig().getSigningKeyStorePassword().toCharArray());
			assertKeyStoreContent(keyStore);
			return keyStore;
		}
		catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	private void assertKeyStoreContent(KeyStore keyStore)
	{
		try
		{
			String alias = yonaProperties.getAppleMobileConfig().getSigningAlias();
			if (!keyStore.isKeyEntry(alias))
			{
				logAliases(keyStore);
				throw ConfigurationException.missingKeyInKeyStore(alias);
			}
		}
		catch (KeyStoreException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	private void logAliases(KeyStore keyStore)
	{
		try
		{
			Enumeration<String> aliasEnum = keyStore.aliases();
			while (aliasEnum.hasMoreElements())
			{
				logger.info("Key store contains alias '{}'", aliasEnum.nextElement());
			}
		}
		catch (KeyStoreException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	@Bean
	@Qualifier("appleMobileConfigSigningCertificate")
	public X509Certificate appleMobileConfigSigningCertificate(KeyStore keyStore)
	{
		try
		{
			return (X509Certificate) keyStore.getCertificate(yonaProperties.getAppleMobileConfig().getSigningAlias());
		}
		catch (KeyStoreException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	@Bean
	@Qualifier("appleMobileConfigSignerKey")
	public PrivateKey appleMobileConfigSignerKey(KeyStore keyStore)
	{
		try
		{
			return (PrivateKey) keyStore.getKey(yonaProperties.getAppleMobileConfig().getSigningAlias(),
					yonaProperties.getAppleMobileConfig().getSigningKeyStorePassword().toCharArray());
		}
		catch (UnrecoverableKeyException | KeyStoreException | NoSuchAlgorithmException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	@Bean
	@Qualifier("appleMobileConfigCaCertificate")
	public X509Certificate appleMobileConfigCaCertificate()
	{
		return loadCertificateFromFile(yonaProperties.getAppleMobileConfig().getCaCertificateFile(), "Apple mobile config CA");
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

	@Bean
	@Qualifier("sslRootCertificate")
	public X509Certificate sslRootCertificate()
	{
		return loadCertificateFromFile(yonaProperties.getSecurity().getSslRootCertFile(), "SSL root");
	}

	private X509Certificate loadCertificateFromFile(String fileName, String description)
	{
		logger.info("Loading {} certificate from {}", description, fileName);
		try (InputStream inStream = new FileInputStream(fileName))
		{
			return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(inStream);
		}
		catch (IOException | CertificateException e)
		{
			throw YonaException.unexpected(e);
		}
	}
}
