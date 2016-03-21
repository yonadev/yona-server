/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.crypto;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

@Converter
public class LongFieldEncrypter implements AttributeConverter<Long, String>
{
	private StringFieldEncrypter stringFieldEncrypter = new StringFieldEncrypter();

	@Override
	public String convertToDatabaseColumn(Long attribute)
	{
		return (attribute == null) ? null : stringFieldEncrypter.convertToDatabaseColumn(attribute.toString());
	}

	@Override
	public Long convertToEntityAttribute(String dbData)
	{
		return (dbData == null) ? null : decryptToLong(dbData);
	}

	private long decryptToLong(String dbData)
	{
		String decryptedString = stringFieldEncrypter.convertToEntityAttribute(dbData);
		return (decryptedString == null) ? 0 : tryToConvertToLong(decryptedString);
	}

	private long tryToConvertToLong(String decryptedString)
	{
		try
		{
			return Long.parseLong(decryptedString);
		}
		catch (Exception e)
		{
			return 0;
		}
	}
}
