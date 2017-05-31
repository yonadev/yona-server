/*******************************************************************************
 * Copyright (c) 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.rest;

import java.io.IOException;
import java.security.PrivateKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.SignerInfoGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import nu.yona.server.exceptions.YonaException;

@Service
public class AppleMobileConfigSigner
{
	@Autowired()
	@Qualifier("appleMobileConfigSigningCertificate")
	X509Certificate signerCertificate;

	@Autowired
	@Qualifier("appleMobileConfigSignerKey")
	PrivateKey signerKey;

	@Autowired()
	@Qualifier("appleMobileConfigCaCertificate")
	X509Certificate caCertificate;

	public byte[] sign(byte[] unsignedMobileconfig)
	{
		try
		{
			CMSSignedDataGenerator generator = createSignedDataGenerator();
			return generator.generate(new CMSProcessableByteArray(unsignedMobileconfig), true).getEncoded();
		}
		catch (IOException | CMSException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	private CMSSignedDataGenerator createSignedDataGenerator()
	{
		try
		{
			SignerInfoGenerator signerInfoGenerator = createSignerInfoGenerator();
			CMSSignedDataGenerator signedDataGenerator = new CMSSignedDataGenerator();
			signedDataGenerator.addSignerInfoGenerator(signerInfoGenerator);
			signedDataGenerator.addCertificate(inHolder(signerCertificate));
			signedDataGenerator.addCertificate(inHolder(caCertificate));
			return signedDataGenerator;
		}
		catch (CMSException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	private X509CertificateHolder inHolder(X509Certificate certificate)
	{
		try
		{
			return new X509CertificateHolder(certificate.getEncoded());
		}
		catch (CertificateEncodingException | IOException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	private SignerInfoGenerator createSignerInfoGenerator()
	{
		try
		{
			ContentSigner sha1Signer = createContentSigner();

			JcaDigestCalculatorProviderBuilder digestProviderBuilder = new JcaDigestCalculatorProviderBuilder().setProvider("BC");
			JcaSignerInfoGeneratorBuilder signerInfoGeneratorBuilder = new JcaSignerInfoGeneratorBuilder(
					digestProviderBuilder.build());

			return signerInfoGeneratorBuilder.build(sha1Signer, signerCertificate);
		}
		catch (CertificateException | OperatorCreationException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	private ContentSigner createContentSigner()
	{
		try
		{
			return new JcaContentSignerBuilder("SHA1withRSA").setProvider("BC").build(signerKey);
		}
		catch (OperatorCreationException e)
		{
			throw YonaException.unexpected(e);
		}

	}
}
