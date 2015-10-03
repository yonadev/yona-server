/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.crypto;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

@Converter
public class LongFieldEncrypter implements AttributeConverter<Long, String> {
	private StringFieldEncrypter stringFieldEncrypter = new StringFieldEncrypter();

	@Override
	public String convertToDatabaseColumn(Long attribute) {
		return (attribute == null) ? null : stringFieldEncrypter.convertToDatabaseColumn(attribute.toString());
	}

	@Override
	public Long convertToEntityAttribute(String dbData) {
		return (dbData == null) ? null : Long.parseLong(stringFieldEncrypter.convertToEntityAttribute(dbData));
	}
}
