/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.service;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.context.i18n.LocaleContextHolder;

import nu.yona.server.goals.entities.ActivityCategory;
import nu.yona.server.goals.entities.ActivityCategoryRepository;

public abstract class ActivityCategoryServiceTestBase
{
	protected Set<ActivityCategory> activityCategories = new HashSet<ActivityCategory>();

	protected ActivityCategory gambling;
	protected ActivityCategory news;

	protected Locale getCurrentLocale()
	{
		return LocaleContextHolder.getLocale();
	}

	protected void setUp(ActivityCategoryRepository mockRepository)
	{
		gambling = ActivityCategory.createInstance(UUID.randomUUID(), localeString("gambling"), false,
				new HashSet<String>(Arrays.asList("poker", "lotto")), Collections.emptySet());
		news = ActivityCategory.createInstance(UUID.randomUUID(), localeString("news"), false,
				new HashSet<String>(Arrays.asList("refdag", "bbc")), Collections.emptySet());

		activityCategories.add(gambling);
		activityCategories.add(news);

		when(mockRepository.findAll()).thenReturn(Collections.unmodifiableSet(activityCategories));
		when(mockRepository.findOne(gambling.getID())).thenReturn(gambling);
		when(mockRepository.findOne(news.getID())).thenReturn(news);
		when(mockRepository.save(any(ActivityCategory.class))).thenAnswer(new Answer<ActivityCategory>() {
			@Override
			public ActivityCategory answer(InvocationOnMock invocation) throws Throwable
			{
				Object[] args = invocation.getArguments();
				return (ActivityCategory) args[0];
			}
		});
	}

	protected Map<Locale, String> localeString(String string)
	{
		return Collections.singletonMap(getCurrentLocale(), string);
	}
}
