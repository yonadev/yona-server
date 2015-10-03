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
import java.security.PrivateKey;
import java.util.UUID;

import javax.crypto.Cipher;

import nu.yona.server.exceptions.YonaException;

public class PublicKeyDecryptor implements Decryptor {

	private PrivateKey privateKey;

	private PublicKeyDecryptor(PrivateKey privateKey) {
		this.privateKey = privateKey;
	}

	public static PublicKeyDecryptor createInstance(PrivateKey privateKey) {
		return new PublicKeyDecryptor(privateKey);
	}

	@Override
	public byte[] decrypt(byte[] ciphertext) {
		try {
			if (ciphertext == null) {
				return null;
			}
			Cipher decryptCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
			decryptCipher.init(Cipher.DECRYPT_MODE, privateKey);

			return decryptCipher.doFinal(ciphertext);
		} catch (GeneralSecurityException e) {
			throw new YonaException(e);
		}
	}

	@Override
	public String decryptString(byte[] ciphertext) {
		return (ciphertext == null) ? null : new String(decrypt(ciphertext), StandardCharsets.UTF_8);
	}

	@Override
	public UUID decryptUUID(byte[] ciphertext) {
		return (ciphertext == null) ? null : UUID.fromString(decryptString(ciphertext));
	}

	@Override
	public long decryptLong(byte[] ciphertext) {
		return Long.parseLong(decryptString(ciphertext));
	}
}
