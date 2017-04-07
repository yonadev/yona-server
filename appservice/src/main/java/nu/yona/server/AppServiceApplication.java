/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.annotation.PostConstruct;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMDecryptorProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMException;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.X509TrustedCertificateBlock;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import nu.yona.server.properties.AppleMobileConfigProperties;
import nu.yona.server.properties.PropertyInitializer;
import nu.yona.server.properties.YonaProperties;

@SpringBootApplication
@EnableCaching
public class AppServiceApplication
{
	private static final Logger logger = LoggerFactory.getLogger(AppServiceApplication.class);
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
	@Qualifier("appleMobileConfigSigningCertificate")
	public X509Certificate appleMobileConfigSigningCertificate()
	{
		try
		{
			AppleMobileConfigProperties properties = yonaProperties.getAppleMobileConfig();
			if (!properties.isSigningEnabled())
			{
				logger.info("Apple mobile config signing certificate not loaded, as signing is disabled");
				return null;
			}
			String signingCertificateFile = properties.getSigningCertificateFile();
			return new JcaX509CertificateConverter().getCertificate(loadSignerCertificate(signingCertificateFile));
		}
		catch (CertificateException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	@Bean
	@Qualifier("appleMobileConfigSignerKey")
	public PrivateKey appleMobileConfigSignerKey()
	{
		try
		{
			AppleMobileConfigProperties properties = yonaProperties.getAppleMobileConfig();
			if (!properties.isSigningEnabled())
			{
				logger.info("Apple mobile config signing key not loaded, as signing is disabled");
				return null;
			}
			String signingKeyFile = properties.getSigningKeyFile();
			String password = properties.getSigningKeyPassword();
			return new JcaPEMKeyConverter().getPrivateKey(loadPrivateKey(signingKeyFile, password));
		}
		catch (PEMException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	private X509CertificateHolder loadSignerCertificate(String fileName)
	{
		try (PEMParser parser = new PEMParser(new FileReader(fileName)))
		{
			return ((X509TrustedCertificateBlock) parser.readObject()).getCertificateHolder();
		}
		catch (IOException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	private PrivateKeyInfo loadPrivateKey(String fileName, String password)
	{
		try (PEMParser parser = new PEMParser(new FileReader(fileName)))
		{
			PEMDecryptorProvider decryptionProv = new JcePEMDecryptorProviderBuilder().build(password.toCharArray());
			return ((PEMEncryptedKeyPair) parser.readObject()).decryptKeyPair(decryptionProv).getPrivateKeyInfo();
		}
		catch (IOException e)
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
}