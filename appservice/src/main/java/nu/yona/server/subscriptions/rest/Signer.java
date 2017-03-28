package nu.yona.server.subscriptions.rest;

import java.io.FileReader;
import java.io.IOException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.SignerInfoGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.openssl.PEMDecryptorProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMException;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.X509TrustedCertificateBlock;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

import nu.yona.server.exceptions.YonaException;

public class Signer
{
	private final String signingCertificateFile;
	private final String signingKeyFile;
	private final String password;

	public Signer(String signingCertificateFile, String signingKeyFile, String password)
	{
		this.signingCertificateFile = signingCertificateFile;
		this.signingKeyFile = signingKeyFile;
		this.password = password;
	}

	public byte[] sign(byte[] unsignedMobileconfig)
	{
		try
		{
			CMSSignedDataGenerator generator = createSignedDataGenerator(signingCertificateFile, signingKeyFile, password);
			return generator.generate(new CMSProcessableByteArray(unsignedMobileconfig), true).getEncoded();
		}
		catch (IOException | CMSException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	private CMSSignedDataGenerator createSignedDataGenerator(String signingCertificateFile, String signingKeyFile,
			String password)
	{
		try
		{
			X509Certificate signerCertificate = createSignerCertificate(signingCertificateFile);
			SignerInfoGenerator signerInfoGenerator = createSignerInfoGenerator(signerCertificate, signingKeyFile, password);
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

	private X509Certificate createSignerCertificate(String signingCertificateFile)
	{
		try
		{
			return new JcaX509CertificateConverter().getCertificate(loadSignerCertificate(signingCertificateFile));
		}
		catch (CertificateException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	private SignerInfoGenerator createSignerInfoGenerator(X509Certificate signerCertificate, String signingKeyFile,
			String password)
	{
		try
		{
			ContentSigner sha1Signer = createContentSigner(signingKeyFile, password);

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

	private ContentSigner createContentSigner(String signingKeyFile, String password)
	{
		try
		{
			PrivateKey signerKey = new JcaPEMKeyConverter().getPrivateKey(loadPrivateKey(signingKeyFile, password));
			return new JcaContentSignerBuilder("SHA1withRSA").setProvider("BC").build(signerKey);
		}
		catch (OperatorCreationException | PEMException e)
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
}
