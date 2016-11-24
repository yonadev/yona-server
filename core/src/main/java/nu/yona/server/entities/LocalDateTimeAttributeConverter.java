/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.entities;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

@Converter(autoApply = false)
public class LocalDateTimeAttributeConverter implements AttributeConverter<LocalDateTime, Timestamp>
{

	@Override
	public Timestamp convertToDatabaseColumn(LocalDateTime entityValue)
	{
		if (entityValue != null)
		{
			return Timestamp.valueOf(entityValue);
		}
		return null;
	}

	@Override
	public LocalDateTime convertToEntityAttribute(Timestamp databaseValue)
	{
		if (databaseValue != null)
		{
			return databaseValue.toLocalDateTime();
		}
		return null;
	}
}