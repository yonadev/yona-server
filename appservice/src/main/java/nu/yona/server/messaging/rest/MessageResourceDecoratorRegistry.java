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

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import nu.yona.server.exceptions.YonaException;
import nu.yona.server.messaging.service.MessageDto;

@Component
public class MessageResourceDecoratorRegistry
{
	private final Map<Class<?>, MessageResourceDecorator> decoratorByType = new HashMap<>();
	private final Map<Class<?>, Set<MessageResourceDecorator>> decoratorsByConcreteType = new HashMap<>();

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
			@SuppressWarnings("unchecked") Class<? extends MessageResourceDecorator> decoratorClass = (Class<? extends MessageResourceDecorator>) Class.forName(
					bd.getBeanClassName());
			MessageResourceDecorator decorator = decoratorClass.getDeclaredConstructor().newInstance();
			Class<? extends MessageDto> decoratedClass = decoratorClass.getAnnotation(Decorates.class).value();
			assertNoDecoratorExists(decoratedClass, decorator);
			decoratorByType.put(decoratedClass, decorator);
		}
		catch (ClassNotFoundException | InstantiationException | IllegalAccessException | IllegalArgumentException |
			   InvocationTargetException | NoSuchMethodException | SecurityException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	private void assertNoDecoratorExists(Class<? extends MessageDto> decoratedClass, MessageResourceDecorator decoratorToRegister)
	{
		MessageResourceDecorator existingDecorator = decoratorByType.get(decoratedClass);
		if (existingDecorator != null)
		{
			throw new IllegalStateException(
					"Duplicate decorators for class " + decoratedClass.getName() + ": " + existingDecorator.getClass().getName()
							+ " and " + decoratorToRegister.getClass().getName());
		}
	}

	public Set<MessageResourceDecorator> getDecorators(Class<? extends MessageDto> classToDecorate)
	{
		synchronized (decoratorsByConcreteType)
		{
			Set<MessageResourceDecorator> decorators = decoratorsByConcreteType.get(classToDecorate);
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