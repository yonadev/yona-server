/*******************************************************************************
 * Copyright (c) 2015, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.service;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.i18n.LocaleContextHolder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import nu.yona.server.goals.entities.ActivityCategory;

@JsonRootName("activityCategory")
public class ActivityCategoryDto implements Serializable
{
	public interface AdminView
	{
		// Marker, nothing is needed here
	}

	public interface AppView
	{
		// Marker, nothing is needed here
	}

	private static final long serialVersionUID = 2498926948887006481L;

	private UUID id;
	private final Map<Locale, String> localizableName;
	private final boolean mandatoryNoGo;
	private final Set<String> smoothwallCategories;
	private final Set<String> applications;
	private final Map<Locale, String> localizableDescription;

	@JsonCreator
	public ActivityCategoryDto(@JsonProperty("id") UUID id,
			@JsonProperty("localizableName") @JsonDeserialize(as = HashMap.class, keyAs = String.class, contentAs = String.class) HashMap<String, String> localizableName,
			@JsonProperty("mandatoryNoGo") boolean mandatory,
			@JsonProperty("smoothwallCategories") @JsonDeserialize(as = TreeSet.class, contentAs = String.class) Set<String> smoothwallCategories,
			@JsonProperty("applications") @JsonDeserialize(as = TreeSet.class, contentAs = String.class) Set<String> applications,
			@JsonProperty("localizableDescription") @JsonDeserialize(as = HashMap.class, keyAs = String.class, contentAs = String.class) HashMap<String, String> localizableDescription)
	{
		this(id, mapToLocaleMap(localizableName), mandatory, smoothwallCategories, applications,
				mapToLocaleMap(localizableDescription));
	}

	public ActivityCategoryDto(UUID id, Map<Locale, String> localizableName, boolean mandatory, Set<String> smoothwallCategories,
			Set<String> applications, Map<Locale, String> localizableDescription)
	{
		this.id = id;
		this.localizableName = localizableName;
		this.mandatoryNoGo = mandatory;
		this.smoothwallCategories = new HashSet<>(smoothwallCategories);
		this.applications = new HashSet<>(applications);
		this.localizableDescription = localizableDescription;
	}

	private static Map<Locale, String> mapToLocaleMap(Map<String, String> localeStringMap)
	{
		return localeStringMap.entrySet().stream()
				.collect(Collectors.toMap(e -> Locale.forLanguageTag(e.getKey()), Map.Entry::getValue));
	}

	private static Map<String, String> mapToStringMap(Map<Locale, String> localeMap)
	{
		return localeMap.entrySet().stream().collect(Collectors.toMap(e -> e.getKey().toLanguageTag(), Map.Entry::getValue));
	}

	@JsonIgnore
	public UUID getId()
	{
		return id;
	}

	public void setId(UUID id)
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

	@JsonView(AdminView.class)
	public Map<String, String> getLocalizableDescription()
	{
		return mapToStringMap(localizableDescription);
	}

	@JsonIgnore
	public Map<Locale, String> getLocalizableDescriptionByLocale()
	{
		return Collections.unmodifiableMap(localizableDescription);
	}

	@JsonView(AppView.class)
	public String getDescription()
	{
		String retVal = localizableDescription.get(LocaleContextHolder.getLocale());
		assert retVal != null;
		return retVal;
	}

	public static ActivityCategoryDto createInstance(ActivityCategory activityCategoryEntity)
	{
		return new ActivityCategoryDto(activityCategoryEntity.getId(), activityCategoryEntity.getLocalizableName(),
				activityCategoryEntity.isMandatoryNoGo(), activityCategoryEntity.getSmoothwallCategories(),
				activityCategoryEntity.getApplications(), activityCategoryEntity.getLocalizableDescription());
	}

	public ActivityCategory createActivityCategoryEntity()
	{
		return ActivityCategory.createInstance(id, localizableName, mandatoryNoGo, smoothwallCategories, applications,
				localizableDescription);
	}

	public ActivityCategory updateActivityCategory(ActivityCategory originalActivityCategoryEntity)
	{
		originalActivityCategoryEntity.setLocalizableName(localizableName);
		originalActivityCategoryEntity.setMandatoryNoGo(mandatoryNoGo);
		originalActivityCategoryEntity.setSmoothwallCategories(smoothwallCategories);
		originalActivityCategoryEntity.setApplications(applications);
		originalActivityCategoryEntity.setLocalizableDescription(localizableDescription);

		return originalActivityCategoryEntity;
	}
}
