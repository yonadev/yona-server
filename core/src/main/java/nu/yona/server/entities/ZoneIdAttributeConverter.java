/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.entities;

import java.time.ZoneId;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter // Autoapply fails with Liquibase, so do not use that
public class ZoneIdAttributeConverter implements AttributeConverter<ZoneId, String>
{

	@Override
	public String convertToDatabaseColumn(ZoneId entityValue)
	{
		if (entityValue != null)
		{
			return entityValue.getId();
		}
		return null;
	}

	@Override
	public ZoneId convertToEntityAttribute(String databaseValue)
	{
		if (databaseValue != null)
		{
			return ZoneId.of(databaseValue);
		}
		return null;
	}
}