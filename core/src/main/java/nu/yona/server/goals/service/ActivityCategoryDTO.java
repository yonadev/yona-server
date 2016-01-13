/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.service;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

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
	private final String name;
	private boolean mandatory;
	private Set<String> smoothwallCategories;
	private Set<String> applications;

	private ActivityCategoryDTO(UUID id, String name, boolean mandatory, Set<String> smoothwallCategories,
			Set<String> applications)
	{
		this.id = id;
		this.name = name;
		this.mandatory = mandatory;
		this.smoothwallCategories = new HashSet<>(smoothwallCategories);
		this.applications = new HashSet<>(applications);
	}

	@JsonCreator
	public ActivityCategoryDTO(@JsonProperty("name") String name, @JsonProperty("mandatory") boolean mandatory,
			@JsonProperty("smoothwallCategories") @JsonDeserialize(as = TreeSet.class, contentAs = String.class) Set<String> smoothwallCategories,
			@JsonProperty("applications") @JsonDeserialize(as = TreeSet.class, contentAs = String.class) Set<String> applications)
	{
		this(null, name, mandatory, smoothwallCategories, applications);
	}

	@JsonIgnore
	public UUID getID()
	{
		return id;
	}

	public String getName()
	{
		return name;
	}

	public boolean getMandatory()
	{
		return mandatory;
	}

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
				activityCategoryEntity.isMandatory(), activityCategoryEntity.getSmoothwallCategories(),
				activityCategoryEntity.getApplications());
	}

	public ActivityCategory createActivityCategoryEntity()
	{
		return ActivityCategory.createInstance(name, mandatory, smoothwallCategories, applications);
	}

	public ActivityCategory updateActivityCategory(ActivityCategory originalActivityCategoryEntity)
	{
		originalActivityCategoryEntity.setName(name);
		originalActivityCategoryEntity.setSmoothwallCategories(smoothwallCategories);

		return originalActivityCategoryEntity;
	}
}
