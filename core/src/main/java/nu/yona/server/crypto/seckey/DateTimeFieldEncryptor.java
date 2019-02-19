/*******************************************************************************
 * Copyright (c) 2015, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.crypto.seckey;

import java.time.LocalDateTime;
import java.util.Base64;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

@Converter
public class DateTimeFieldEncryptor implements AttributeConverter<LocalDateTime, String>
{
	@Override
	public String convertToDatabaseColumn(LocalDateTime attribute)
	{
		return (attribute == null) ? null : Base64.getEncoder().encodeToString(SecretKeyUtil.encryptDateTime(attribute));
	}

	@Override
	public LocalDateTime convertToEntityAttribute(String dbData)
	{
		try
		{
			if (dbData == null)
			{
				return null;
			}

			return SecretKeyUtil.decryptDateTime(Base64.getDecoder().decode(dbData));
		}
		catch (RuntimeException ex)
		{
			return null;
		}
	}
}
