/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.entities;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.Table;

import nu.yona.server.entities.EntityWithID;
import nu.yona.server.entities.RepositoryProvider;

@Entity
@Table(name = "ACTIVITYCATEGORIES")
public class ActivityCategory extends EntityWithID
{
	public static ActivityCategoryRepository getRepository()
	{
		return (ActivityCategoryRepository) RepositoryProvider.getRepository(ActivityCategory.class, UUID.class);
	}

	@Column(unique = true)
	private String name;

	private boolean mandatoryNoGo;

	@ElementCollection
	private Set<String> smoothwallCategories;

	@ElementCollection
	private Set<String> applications;

	// Default constructor is required for JPA
	public ActivityCategory()
	{
		super(null);
	}

	public ActivityCategory(UUID id, String name, boolean mandatoryNoGo, Set<String> smoothwallCategories, Set<String> applications)
	{
		super(id);
		this.name = name;
		this.mandatoryNoGo = mandatoryNoGo;
		this.smoothwallCategories = new HashSet<>(smoothwallCategories);
		this.applications = new HashSet<>(applications);
	}

	public String getName()
	{
		return name;
	}

	public boolean isMandatoryNoGo()
	{
		return mandatoryNoGo;
	}

	public Set<String> getSmoothwallCategories()
	{
		return Collections.unmodifiableSet(smoothwallCategories);
	}

	public Set<String> getApplications()
	{
		return Collections.unmodifiableSet(applications);
	}

	public void setName(String name)
	{
		this.name = name;
	}

	public void setSmoothwallCategories(Set<String> smoothwallCategories)
	{
		this.smoothwallCategories = new HashSet<>(smoothwallCategories);
	}

	public static ActivityCategory createInstance(String name, boolean mandatoryNoGo, Set<String> smoothwallCategories,
			Set<String> applications)
	{
		return new ActivityCategory(UUID.randomUUID(), name, mandatoryNoGo, smoothwallCategories, applications);
	}
}
