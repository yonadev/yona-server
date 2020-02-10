/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.rest;

import org.springframework.hateoas.RepresentationModel;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/*
 * Class to extend from when a DTO is polymorphic. Do not forget to add an @JsonSubTypes annotation.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "@type")
public abstract class PolymorphicDto extends RepresentationModel<PolymorphicDto>
{
	/*
	 * Used to serialize the type. The default way with @JsonTypeInfo did not work.
	 */
	@JsonProperty(value = "@type")
	public abstract String getType();

	/*
	 * Revert to default equals implementation.
	 * @see org.springframework.hateoas.RepresentationModel#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj)
	{
		return this == obj;
	}

	/*
	 * Revert to default hash code implementation.
	 * @see org.springframework.hateoas.RepresentationModel#hashCode()
	 */
	@Override
	public int hashCode()
	{
		return System.identityHashCode(this);
	}
}
