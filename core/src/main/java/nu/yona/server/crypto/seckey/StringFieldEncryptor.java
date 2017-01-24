/*******************************************************************************
 * Copyright (c) 2015, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.crypto.seckey;

import java.util.Base64;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

@Converter
public class StringFieldEncryptor implements AttributeConverter<String, String>
{
	@Override
	public String convertToDatabaseColumn(String attribute)
	{
		return (attribute == null) ? null : Base64.getEncoder().encodeToString(SecretKeyUtil.encryptString(attribute));
	}

	@Override
	public String convertToEntityAttribute(String dbData)
	{
		try
		{
			if (dbData == null)
			{
				return null;
			}

			return SecretKeyUtil.decryptString(Base64.getDecoder().decode(dbData));
		}
		catch (RuntimeException ex)
		{
			return null;
		}
	}
}
