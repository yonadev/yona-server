/*******************************************************************************
 * Copyright (c) 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.messaging.rest;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

import nu.yona.server.exceptions.YonaException;
import nu.yona.server.messaging.service.MessageDto;

@Component
public class DecoratorRegistry
{
	private Map<Class<?>, Decorator> decoratorByType = new HashMap<>();
	private Map<Class<?>, Set<Decorator>> decoratorsByConcreteType = new HashMap<>();

	@PostConstruct
	private void initializeDecoratorCollection()
	{
		ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);

		scanner.addIncludeFilter(new AnnotationTypeFilter(Decorates.class));

		scanner.findCandidateComponents(this.getClass().getPackageName()).forEach(this::registerDecorator);
	}

	private void registerDecorator(BeanDefinition bd)
	{
		try
		{
			@SuppressWarnings("unchecked")
			Class<? extends Decorator> decoratorClass = (Class<? extends Decorator>) Class.forName(bd.getBeanClassName());
			Decorator decorator = decoratorClass.getDeclaredConstructor().newInstance();
			Class<? extends MessageDto> decoratedClass = decoratorClass.getAnnotation(Decorates.class).value();
			assertNoDecoratorExists(decoratedClass, decorator);
			decoratorByType.put(decoratedClass, decorator);
		}
		catch (ClassNotFoundException | InstantiationException | IllegalAccessException | IllegalArgumentException
				| InvocationTargetException | NoSuchMethodException | SecurityException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	private void assertNoDecoratorExists(Class<? extends MessageDto> decoratedClass, Decorator decoratorToRegister)
	{
		Decorator existingDecorator = decoratorByType.get(decoratedClass);
		if (existingDecorator != null)
		{
			throw new IllegalStateException("Duplicate decorators for class " + decoratedClass.getName() + ": "
					+ existingDecorator.getClass().getName() + " and " + decoratorToRegister.getClass().getName());
		}
	}

	public Set<Decorator> getDecorators(Class<? extends MessageDto> classToDecorate)
	{
		synchronized (decoratorsByConcreteType)
		{
			Set<Decorator> decorators = decoratorsByConcreteType.get(classToDecorate);
			if (decorators != null)
			{
				return decorators;
			}
			decorators = decoratorByType.entrySet().stream().filter(e -> e.getKey().isAssignableFrom(classToDecorate))
					.map(Map.Entry::getValue).collect(Collectors.toSet());
			decoratorsByConcreteType.put(classToDecorate, decorators);
			return decorators;
		}
	}
}