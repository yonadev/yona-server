/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.springframework.context.i18n.LocaleContextHolder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import nu.yona.server.goals.entities.ActivityCategory;

@JsonRootName("activityCategory")
public class ActivityCategoryDTO
{
	private final UUID id;
	private final Map<Locale, String> name;
	private boolean mandatoryNoGo;
	private Set<String> smoothwallCategories;
	private Set<String> applications;

	@JsonCreator
	public ActivityCategoryDTO(@JsonProperty("id") UUID id,
			@JsonProperty("name") @JsonDeserialize(as = HashMap.class, keyAs = String.class, contentAs = String.class) HashMap<String, String> name,
			@JsonProperty("mandatoryNoGo") boolean mandatory,
			@JsonProperty("smoothwallCategories") @JsonDeserialize(as = TreeSet.class, contentAs = String.class) Set<String> smoothwallCategories,
			@JsonProperty("applications") @JsonDeserialize(as = TreeSet.class, contentAs = String.class) Set<String> applications)
	{
		this(id, mapToLocaleMap(name), mandatory, smoothwallCategories, applications);
	}

	public ActivityCategoryDTO(UUID id, Map<Locale, String> name, boolean mandatory, Set<String> smoothwallCategories,
			Set<String> applications)
	{
		this.id = id;
		this.name = name;
		this.mandatoryNoGo = mandatory;
		this.smoothwallCategories = new HashSet<>(smoothwallCategories);
		this.applications = new HashSet<>(applications);
	}

	private static Map<Locale, String> mapToLocaleMap(Map<String, String> localeStringMap)
	{
		Map<Locale, String> localeMap = new HashMap<>();
		for (String languageTag : localeStringMap.keySet())
		{
			localeMap.put(Locale.forLanguageTag(languageTag), localeStringMap.get(languageTag));
		}
		return localeMap;
	}

	@JsonIgnore
	public UUID getID()
	{
		return id;
	}

	public String getName()
	{
		String retVal = name.get(LocaleContextHolder.getLocale());
		assert retVal != null;
		return retVal;
	}

	public String getName(Locale locale)
	{
		return name.get(locale);
	}

	@JsonIgnore
	public boolean isMandatoryNoGo()
	{
		return mandatoryNoGo;
	}

	@JsonIgnore
	public Set<String> getSmoothwallCategories()
	{
		return Collections.unmodifiableSet(smoothwallCategories);
	}

	public Set<String> getApplications()
	{
		return Collections.unmodifiableSet(applications);
	}

	public static ActivityCategoryDTO createInstance(ActivityCategory activityCategoryEntity)
	{
		return new ActivityCategoryDTO(activityCategoryEntity.getID(), activityCategoryEntity.getName(),
				activityCategoryEntity.isMandatoryNoGo(), activityCategoryEntity.getSmoothwallCategories(),
				activityCategoryEntity.getApplications());
	}

	public ActivityCategory createActivityCategoryEntity()
	{
		return ActivityCategory.createInstance(id, name, mandatoryNoGo, smoothwallCategories, applications);
	}

	public ActivityCategory updateActivityCategory(ActivityCategory originalActivityCategoryEntity)
	{
		originalActivityCategoryEntity.setName(name);
		originalActivityCategoryEntity.setSmoothwallCategories(smoothwallCategories);

		return originalActivityCategoryEntity;
	}
}
