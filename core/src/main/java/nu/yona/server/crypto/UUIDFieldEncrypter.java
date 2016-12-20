/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.crypto;

import java.util.UUID;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

@Converter
public class UUIDFieldEncrypter implements AttributeConverter<UUID, String>
{
	private StringFieldEncrypter stringFieldEncrypter = new StringFieldEncrypter();

	@Override
	public String convertToDatabaseColumn(UUID attribute)
	{
		return (attribute == null) ? null : stringFieldEncrypter.convertToDatabaseColumn(attribute.toString());
	}

	@Override
	public UUID convertToEntityAttribute(String dbData)
	{
		return (dbData == null) ? null : decryptToUuid(dbData);
	}

	private UUID decryptToUuid(String dbData)
	{
		String decryptedString = stringFieldEncrypter.convertToEntityAttribute(dbData);
		return (decryptedString == null) ? null : tryToConvertToUuid(decryptedString);
	}

	private UUID tryToConvertToUuid(String decryptedString)
	{
		try
		{
			return UUID.fromString(decryptedString);
		}
		catch (Exception e)
		{
			return null;
		}
	}
}
