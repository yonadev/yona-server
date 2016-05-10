/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.service;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import nu.yona.server.goals.entities.ActivityCategory;
import nu.yona.server.goals.entities.ActivityCategoryRepository;

@Service
public class ActivityCategoryService
{
	@Autowired
	private ActivityCategoryRepository repository;

	private static final Logger logger = LoggerFactory.getLogger(ActivityCategoryService.class);

	@Transactional
	public ActivityCategoryDTO getActivityCategory(UUID id)
	{
		ActivityCategory activityCategoryEntity = getEntityByID(id);
		return ActivityCategoryDTO.createInstance(activityCategoryEntity);
	}

	@Cacheable(value = "activityCategorySet", key = "'instance'")
	@Transactional
	public Set<ActivityCategoryDTO> getAllActivityCategories()
	{
		Set<ActivityCategoryDTO> activityCategories = new HashSet<ActivityCategoryDTO>();
		for (ActivityCategory activityCategory : repository.findAll())
		{
			activityCategories.add(ActivityCategoryDTO.createInstance(activityCategory));
		}
		return activityCategories;
	}

	@CacheEvict(value = "activityCategorySet", key = "'instance'")
	@Transactional
	public ActivityCategoryDTO addActivityCategory(ActivityCategoryDTO activityCategoryDTO)
	{
		logger.info("Adding activity category '{}'", activityCategoryDTO.getName(Locale.forLanguageTag("en-US")));
		return ActivityCategoryDTO.createInstance(repository.save(activityCategoryDTO.createActivityCategoryEntity()));
	}

	@CacheEvict(value = "activityCategorySet", key = "'instance'")
	@Transactional
	public ActivityCategoryDTO updateActivityCategory(UUID id, ActivityCategoryDTO activityCategoryDTO)
	{
		ActivityCategory originalEntity = getEntityByID(id);
		return ActivityCategoryDTO.createInstance(updateActivityCategory(originalEntity, activityCategoryDTO));
	}

	private ActivityCategory updateActivityCategory(ActivityCategory activityCategoryTargetEntity,
			ActivityCategoryDTO activityCategorySourceDTO)
	{
		logger.info("Updating activity category '{}'", activityCategorySourceDTO.getName(Locale.forLanguageTag("en-US")));
		return repository.save(activityCategorySourceDTO.updateActivityCategory(activityCategoryTargetEntity));
	}

	@CacheEvict(value = "activityCategorySet", key = "'instance'")
	@Transactional
	public void deleteActivityCategory(UUID id)
	{
		repository.delete(id);
	}

	private void deleteActivityCategory(ActivityCategory activityCategoryEntity)
	{
		logger.info("Deleting activity category '{}'", activityCategoryEntity.getName());
		repository.delete(activityCategoryEntity);
	}

	private ActivityCategory getEntityByID(UUID id)
	{
		ActivityCategory entity = repository.findOne(id);
		if (entity == null)
		{
			throw ActivityCategoryNotFoundException.notFound(id);
		}
		return entity;
	}

	private Set<ActivityCategory> getAllActivityCategoryEntities()
	{
		Set<ActivityCategory> activityCategories = new HashSet<ActivityCategory>();
		for (ActivityCategory activityCategory : repository.findAll())
		{
			activityCategories.add(activityCategory);
		}
		return activityCategories;
	}

	@CacheEvict(value = "activityCategorySet", key = "'instance'")
	@Transactional
	public void importActivityCategories(Set<ActivityCategoryDTO> activityCategoryDTOs)
	{
		Set<ActivityCategory> activityCategoriesInRepository = getAllActivityCategoryEntities();
		deleteRemovedActivityCategories(activityCategoriesInRepository, activityCategoryDTOs);
		addOrUpdateNewActivityCategories(activityCategoryDTOs, activityCategoriesInRepository);
	}

	private void addOrUpdateNewActivityCategories(Set<ActivityCategoryDTO> activityCategoryDTOs,
			Set<ActivityCategory> activityCategoriesInRepository)
	{
		Map<UUID, ActivityCategory> activityCategoriesInRepositoryMap = activityCategoriesInRepository.stream()
				.collect(Collectors.toMap(ac -> ac.getID(), ac -> ac));

		for (ActivityCategoryDTO activityCategoryDTO : activityCategoryDTOs)
		{
			ActivityCategory activityCategoryInRepository = activityCategoriesInRepositoryMap.get(activityCategoryDTO.getID());
			if (activityCategoryInRepository == null)
			{
				addActivityCategory(activityCategoryDTO);
			}
			else
			{
				if (!activityCategoryInRepository.getSmoothwallCategories().equals(activityCategoryDTO.getSmoothwallCategories()))
				{
					updateActivityCategory(activityCategoryInRepository, activityCategoryDTO);
				}
			}
		}
	}

	private void deleteRemovedActivityCategories(Set<ActivityCategory> activityCategoriesInRepository,
			Set<ActivityCategoryDTO> activityCategoryDTOs)
	{
		Map<UUID, ActivityCategoryDTO> activityCategoryDTOsMap = activityCategoryDTOs.stream()
				.collect(Collectors.toMap(ac -> ac.getID(), ac -> ac));

		activityCategoriesInRepository.stream().filter(ac -> !activityCategoryDTOsMap.containsKey(ac.getID()))
				.forEach(this::deleteActivityCategory);
	}

	public Set<ActivityCategoryDTO> getMatchingCategoriesForSmoothwallCategories(Set<String> smoothwallCategories)
	{
		return getAllActivityCategories().stream().filter(ac -> {
			Set<String> acSmoothwallCategories = new HashSet<>(ac.getSmoothwallCategories());
			acSmoothwallCategories.retainAll(smoothwallCategories);
			return !acSmoothwallCategories.isEmpty();
		}).collect(Collectors.toSet());
	}

	public Set<ActivityCategoryDTO> getMatchingCategoriesForApp(String application)
	{
		return getAllActivityCategories().stream().filter(ac -> ac.getApplications().contains(application))
				.collect(Collectors.toSet());
	}
}
