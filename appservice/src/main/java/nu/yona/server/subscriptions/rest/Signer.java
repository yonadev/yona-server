package nu.yona.server.subscriptions.rest;

import java.io.IOException;
import java.security.PrivateKey;
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
import org.springframework.stereotype.Service;

import nu.yona.server.exceptions.YonaException;

@Service
public class Signer
{
	@Autowired
	X509Certificate signerCertificate;

	@Autowired
	PrivateKey signerKey;

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
			signedDataGenerator.addCertificate(new X509CertificateHolder(signerCertificate.getEncoded()));
			return signedDataGenerator;
		}
		catch (CertificateException | IOException | CMSException e)
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

			SignerInfoGenerator signerInfoGenerator = signerInfoGeneratorBuilder.build(sha1Signer, signerCertificate);
			return signerInfoGenerator;
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
