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
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import nu.yona.server.goals.entities.ActivityCategory;

@JsonRootName("activityCategory")
public class ActivityCategoryDTO
{
	public static class AdminView
	{
	}

	public static class AppView
	{
	}

	private UUID id;
	private final Map<Locale, String> localizableName;
	private final boolean mandatoryNoGo;
	private final Set<String> smoothwallCategories;
	private final Set<String> applications;

	@JsonCreator
	public ActivityCategoryDTO(@JsonProperty("id") UUID id,
			@JsonProperty("localizableName") @JsonDeserialize(as = HashMap.class, keyAs = String.class, contentAs = String.class) HashMap<String, String> localizableName,
			@JsonProperty("mandatoryNoGo") boolean mandatory,
			@JsonProperty("smoothwallCategories") @JsonDeserialize(as = TreeSet.class, contentAs = String.class) Set<String> smoothwallCategories,
			@JsonProperty("applications") @JsonDeserialize(as = TreeSet.class, contentAs = String.class) Set<String> applications)
	{
		this(id, mapToLocaleMap(localizableName), mandatory, smoothwallCategories, applications);
	}

	public ActivityCategoryDTO(UUID id, Map<Locale, String> localizableName, boolean mandatory, Set<String> smoothwallCategories,
			Set<String> applications)
	{
		this.id = id;
		this.localizableName = localizableName;
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

	private static Map<String, String> mapToStringMap(Map<Locale, String> localeMap)
	{
		Map<String, String> localeStringMap = new HashMap<>();
		for (Locale locale : localeMap.keySet())
		{
			localeStringMap.put(locale.toLanguageTag(), localeMap.get(locale));
		}
		return localeStringMap;
	}

	@JsonIgnore
	public UUID getID()
	{
		return id;
	}

	public void setID(UUID id)
	{
		this.id = id;
	}

	@JsonView(AppView.class)
	public String getName()
	{
		String retVal = localizableName.get(LocaleContextHolder.getLocale());
		assert retVal != null;
		return retVal;
	}

	@JsonView(AdminView.class)
	public Map<String, String> getLocalizableName()
	{
		return mapToStringMap(localizableName);
	}

	@JsonIgnore
	public Map<Locale, String> getLocalizableNameByLocale()
	{
		return Collections.unmodifiableMap(localizableName);
	}

	public String getName(Locale locale)
	{
		return localizableName.get(locale);
	}

	@JsonView(AdminView.class)
	public boolean isMandatoryNoGo()
	{
		return mandatoryNoGo;
	}

	@JsonView(AdminView.class)
	public Set<String> getSmoothwallCategories()
	{
		return Collections.unmodifiableSet(smoothwallCategories);
	}

	@JsonView({ AdminView.class, AppView.class })
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
		return ActivityCategory.createInstance(id, localizableName, mandatoryNoGo, smoothwallCategories, applications);
	}

	public ActivityCategory updateActivityCategory(ActivityCategory originalActivityCategoryEntity)
	{
		originalActivityCategoryEntity.setName(localizableName);
		originalActivityCategoryEntity.setMandatoryNoGo(mandatoryNoGo);
		originalActivityCategoryEntity.setSmoothwallCategories(smoothwallCategories);
		originalActivityCategoryEntity.setApplications(applications);

		return originalActivityCategoryEntity;
	}
}
