/*******************************************************************************
 * Copyright (c) 2015, 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.rest;

import java.lang.annotation.Annotation;
import java.util.Arrays;

import org.atteo.evo.inflector.English;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.hateoas.LinkRelation;
import org.springframework.hateoas.server.LinkRelationProvider;
import org.springframework.hateoas.server.core.DefaultLinkRelationProvider;

import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.annotation.JsonSubTypes;

/**
 * When an embedded resource is provided in a response using the {@code org.springframework.hateoas.Resources} model, this
 * provider can be configured at runtime to make any embedded values root JSON name be set based on the classes annotated
 * {@code JsonRootName ( " name " )}. <br/>
 * By default Spring HATEOAS renders the embedded root field based on the class name with first character in lowercase. If the
 * resource is of a subtype that is annotated with @JsonSubTypes, then the name of that basetype is used to generate the name of
 * the embedded resource.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class JsonRootLinkRelationProvider implements LinkRelationProvider
{
	public static final LinkRelation EDIT_REL = LinkRelation.of("edit");

	private final DefaultLinkRelationProvider defaultLinkRelationProvider = new DefaultLinkRelationProvider();

	@Override
	public LinkRelation getItemResourceRelFor(Class<?> type)
	{
		Class<?> baseType = determineBaseType(type);
		JsonRootName rootName = getAnnotationByType(baseType, JsonRootName.class);
		return (rootName == null) ? defaultLinkRelationProvider.getItemResourceRelFor(baseType)
				: LinkRelation.of(rootName.value());
	}

	@Override
	public LinkRelation getCollectionResourceRelFor(Class<?> type)
	{
		return LinkRelation.of(English.plural(getItemResourceRelFor(type).value()));
	}

	@Override
	public boolean supports(LookupContext delimiter)
	{
		return defaultLinkRelationProvider.supports(delimiter);
	}

	private <T extends Annotation> T getAnnotationByType(Class<?> type, Class<T> annotationType)
	{
		T[] annotations = type.getAnnotationsByType(annotationType);
		return (annotations.length == 0) ? null : annotations[0];
	}

	private Class<?> determineBaseType(Class<?> type)
	{
		Class<?> baseType = type;
		while ((baseType = baseType.getSuperclass()) != null)
		{
			JsonSubTypes subtypesAnnotation = getAnnotationByType(baseType, JsonSubTypes.class);
			if (containsType(subtypesAnnotation, type))
			{
				return baseType;
			}
		}
		return type;
	}

	private boolean containsType(JsonSubTypes subtypesAnnotation, Class<?> type)
	{
		return (subtypesAnnotation != null)
				&& (Arrays.asList(subtypesAnnotation.value()).stream().anyMatch(t -> t.value() == type));
	}
}
