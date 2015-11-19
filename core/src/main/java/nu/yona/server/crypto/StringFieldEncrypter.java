/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.crypto;

import java.nio.charset.StandardCharsets;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

@Converter
public class StringFieldEncrypter implements AttributeConverter<String, String>
{
	@Override
	public String convertToDatabaseColumn(String attribute)
	{
		return (attribute == null) ? null
				: new ByteFieldEncrypter().convertToDatabaseColumn(attribute.getBytes(StandardCharsets.UTF_8));
	}

	@Override
	public String convertToEntityAttribute(String dbData)
	{
		return (dbData == null) ? null : decryptToString(dbData);
	}

	private String decryptToString(String dbData)
	{
		byte[] decryptedBytes = new ByteFieldEncrypter().convertToEntityAttribute(dbData);
		return (decryptedBytes == null) ? null : new String(decryptedBytes);
	}
}
