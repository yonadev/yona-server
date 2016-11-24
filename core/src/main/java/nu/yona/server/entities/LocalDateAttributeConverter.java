/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.entities;

import java.time.LocalDate;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

@Converter(autoApply = false)
public class LocalDateAttributeConverter implements AttributeConverter<LocalDate, java.sql.Date>
{

	@Override
	public java.sql.Date convertToDatabaseColumn(LocalDate entityValue)
	{
		if (entityValue != null)
		{
			return java.sql.Date.valueOf(entityValue);
		}
		return null;
	}

	@Override
	public LocalDate convertToEntityAttribute(java.sql.Date databaseValue)
	{
		if (databaseValue != null)
		{
			return databaseValue.toLocalDate();
		}
		return null;
	}
}