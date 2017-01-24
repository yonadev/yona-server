/*******************************************************************************
 * Copyright (c) 2015, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.crypto.seckey;

import java.util.Base64;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

@Converter
public class ByteFieldEncryptor implements AttributeConverter<byte[], String>
{
	@Override
	public String convertToDatabaseColumn(byte[] plaintext)
	{
		if (plaintext == null)
		{
			return null;
		}

		return Base64.getEncoder().encodeToString(SecretKeyUtil.encryptBytes(plaintext));
	}

	@Override
	public byte[] convertToEntityAttribute(String dbData)
	{
		try
		{
			if (dbData == null)
			{
				return null;
			}

			return SecretKeyUtil.decryptBytes(Base64.getDecoder().decode(dbData));
		}
		catch (RuntimeException ex)
		{
			return null;
		}
	}
}
