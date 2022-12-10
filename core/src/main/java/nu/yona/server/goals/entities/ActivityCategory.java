/*******************************************************************************
 * Copyright (c) 2015, 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.entities;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.BatchSize;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import nu.yona.server.entities.EntityWithUuid;
import nu.yona.server.entities.RepositoryProvider;

@Entity
@Table(name = "ACTIVITY_CATEGORIES")
@BatchSize(size = 20)
public class ActivityCategory extends EntityWithUuid
{
	@ElementCollection
	private Map<Locale, String> localizableName;

	private boolean mandatoryNoGo;

	@ElementCollection
	private Set<String> smoothwallCategories;

	@ElementCollection
	private Set<String> applications;

	@ElementCollection
	private Map<Locale, String> localizableDescription;

	// Default constructor is required for JPA
	public ActivityCategory()
	{
		super(null);
	}

	public ActivityCategory(UUID id, Map<Locale, String> localizableName, boolean mandatoryNoGo, Set<String> smoothwallCategories,
			Set<String> applications, Map<Locale, String> localizableDescription)
	{
		super(id);
		setLocalizableName(localizableName);
		this.mandatoryNoGo = mandatoryNoGo;
		setSmoothwallCategories(smoothwallCategories);
		setApplications(applications);
		setLocalizableDescription(localizableDescription);
	}

	public static ActivityCategoryRepository getRepository()
	{
		return (ActivityCategoryRepository) RepositoryProvider.getRepository(ActivityCategory.class, UUID.class);
	}

	public static ActivityCategory createInstance(UUID id, Map<Locale, String> localizableName, boolean mandatoryNoGo,
			Set<String> smoothwallCategories, Set<String> applications, Map<Locale, String> localizableDescription)
	{
		return new ActivityCategory(id, localizableName, mandatoryNoGo, smoothwallCategories, applications,
				localizableDescription);
	}

	public Map<Locale, String> getLocalizableName()
	{
		return new HashMap<>(localizableName);
	}

	public void setLocalizableName(Map<Locale, String> name)
	{
		this.localizableName = new HashMap<>(name);
	}

	public boolean isMandatoryNoGo()
	{
		return mandatoryNoGo;
	}

	public void setMandatoryNoGo(boolean mandatoryNoGo)
	{
		this.mandatoryNoGo = mandatoryNoGo;
	}

	public Set<String> getSmoothwallCategories()
	{
		return Collections.unmodifiableSet(smoothwallCategories);
	}

	public void setSmoothwallCategories(Set<String> smoothwallCategories)
	{
		this.smoothwallCategories = new HashSet<>(smoothwallCategories);
	}

	public Set<String> getApplications()
	{
		return Collections.unmodifiableSet(applications);
	}

	public void setApplications(Set<String> applications)
	{
		this.applications = new HashSet<>(applications);
	}

	public Map<Locale, String> getLocalizableDescription()
	{
		return new HashMap<>(localizableDescription);
	}

	public void setLocalizableDescription(Map<Locale, String> localizableDescription)
	{
		this.localizableDescription = new HashMap<>(localizableDescription);
	}
}
