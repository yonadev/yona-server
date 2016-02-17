/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.rest;

import java.lang.annotation.Annotation;
import java.util.Arrays;

import org.atteo.evo.inflector.English;
import org.springframework.hateoas.RelProvider;
import org.springframework.hateoas.core.DefaultRelProvider;

import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.annotation.JsonSubTypes;

/**
 * When an embedded resource is provided in a response using the {@code org.springframework.hateoas.Resources} model, this
 * provider can be configured at runtime to make any embedded values root json name be set based on the classes annotated
 * {@code JsonRootName ( " name " )}. By default Spring hateoas renders the embedded root field based on the class name with first
 * character in lowercase.
 */
public class JsonRootRelProvider implements RelProvider
{
	public static final String EDIT_REL = "edit";

	private DefaultRelProvider defaultRelProvider = new DefaultRelProvider();

	@Override
	public String getItemResourceRelFor(Class<?> type)
	{
		Class<?> baseType = determineBaseType(type);
		JsonRootName rootName = getAnnotationByType(baseType, JsonRootName.class);
		return (rootName == null) ? defaultRelProvider.getItemResourceRelFor(baseType) : rootName.value();
	}

	@Override
	public String getCollectionResourceRelFor(Class<?> type)
	{
		return English.plural(getItemResourceRelFor(type));
	}

	@Override
	public boolean supports(Class<?> delimiter)
	{
		return defaultRelProvider.supports(delimiter);
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
