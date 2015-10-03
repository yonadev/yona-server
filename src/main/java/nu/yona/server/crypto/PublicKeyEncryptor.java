/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.UUID;

import javax.crypto.Cipher;

import nu.yona.server.exceptions.YonaException;

public class PublicKeyEncryptor implements Encryptor {

	private PublicKey publicKey;

	private PublicKeyEncryptor(PublicKey publicKey) {
		this.publicKey = publicKey;
	}

	public static PublicKeyEncryptor createInstance(PublicKey publicKey) {
		return new PublicKeyEncryptor(publicKey);
	}

	@Override
	public byte[] encrypt(byte[] plaintext) {
		try {
			if (plaintext == null) {
				return null;
			}
			Cipher encryptCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
			encryptCipher.init(Cipher.ENCRYPT_MODE, publicKey);

			return encryptCipher.doFinal(plaintext);
		} catch (GeneralSecurityException e) {
			throw new YonaException(e);
		}
	}

	@Override
	public byte[] encrypt(String plaintext) {
		return (plaintext == null) ? null : encrypt(plaintext.getBytes(StandardCharsets.UTF_8));
	}

	@Override
	public byte[] encrypt(UUID plaintext) {
		return (plaintext == null) ? null : encrypt(plaintext.toString());
	}

	@Override
	public byte[] encrypt(long plaintext) {
		return encrypt(Long.toString(plaintext));
	}
}
