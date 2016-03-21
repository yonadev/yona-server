/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.crypto;

import java.util.Base64;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

@Converter
public class ByteFieldEncrypter implements AttributeConverter<byte[], String>
{
	@Override
	public String convertToDatabaseColumn(byte[] plaintext)
	{
		if (plaintext == null)
		{
			return null;
		}

		byte[] ciphertext = CryptoSession.getCurrent().encrypt(plaintext);
		return Base64.getEncoder().encodeToString(ciphertext);
	}

	@Override
	public byte[] convertToEntityAttribute(String dbData)
	{
		if (dbData == null)
		{
			return null;
		}

		try
		{
			byte[] ciphertext = Base64.getDecoder().decode(dbData);
			return CryptoSession.getCurrent().decrypt(ciphertext);
		}
		catch (RuntimeException ex)
		{
			return null;
		}
	}
}
