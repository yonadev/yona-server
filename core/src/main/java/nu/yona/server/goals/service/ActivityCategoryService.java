/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.service;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import nu.yona.server.goals.entities.ActivityCategory;

@Service
public class ActivityCategoryService
{
	private static final Logger logger = LoggerFactory.getLogger(ActivityCategoryService.class);

	public ActivityCategoryDTO getActivityCategory(UUID id)
	{
		ActivityCategory activityCategoryEntity = getEntityByID(id);
		return ActivityCategoryDTO.createInstance(activityCategoryEntity);
	}

	public Set<ActivityCategoryDTO> getAllActivityCategories()
	{
		Set<ActivityCategoryDTO> activityCategories = new HashSet<ActivityCategoryDTO>();
		for (ActivityCategory activityCategory : ActivityCategory.getRepository().findAll())
		{
			activityCategories.add(ActivityCategoryDTO.createInstance(activityCategory));
		}
		return activityCategories;
	}

	@Transactional
	public ActivityCategoryDTO addActivityCategory(ActivityCategoryDTO activityCategoryDTO)
	{
		logger.info("Adding activity category '{}'", activityCategoryDTO.getName());
		return ActivityCategoryDTO
				.createInstance(ActivityCategory.getRepository().save(activityCategoryDTO.createActivityCategoryEntity()));
	}

	@Transactional
	public ActivityCategoryDTO updateActivityCategory(UUID id, ActivityCategoryDTO activityCategoryDTO)
	{
		ActivityCategory originalEntity = getEntityByID(id);
		return ActivityCategoryDTO.createInstance(updateActivityCategory(originalEntity, activityCategoryDTO));
	}

	private ActivityCategory updateActivityCategory(ActivityCategory activityCategoryTargetEntity,
			ActivityCategoryDTO activityCategorySourceDTO)
	{
		logger.info("Updating activity category '{}'", activityCategorySourceDTO.getName());
		return ActivityCategory.getRepository()
				.save(activityCategorySourceDTO.updateActivityCategory(activityCategoryTargetEntity));
	}

	@Transactional
	public void deleteActivityCategory(UUID id)
	{
		ActivityCategory.getRepository().delete(id);
	}

	private void deleteActivityCategory(ActivityCategory activityCategoryEntity)
	{
		logger.info("Deleting activity category '{}'", activityCategoryEntity.getName());
		ActivityCategory.getRepository().delete(activityCategoryEntity);
	}

	private ActivityCategory getEntityByID(UUID id)
	{
		ActivityCategory entity = ActivityCategory.getRepository().findOne(id);
		if (entity == null)
		{
			throw ActivityCategoryNotFoundException.notFound(id);
		}
		return entity;
	}

	public Set<ActivityCategory> getAllActivityCategoryEntities()
	{
		Set<ActivityCategory> activityCategories = new HashSet<ActivityCategory>();
		for (ActivityCategory activityCategory : ActivityCategory.getRepository().findAll())
		{
			activityCategories.add(activityCategory);
		}
		return activityCategories;
	}

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
		Map<String, ActivityCategory> activityCategoriesInRepositoryMap = activityCategoriesInRepository.stream()
				.collect(Collectors.toMap(ac -> ac.getName(), ac -> ac));

		for (ActivityCategoryDTO activityCategoryDTO : activityCategoryDTOs)
		{
			ActivityCategory activityCategoryInRepository = activityCategoriesInRepositoryMap.get(activityCategoryDTO.getName());
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
		Map<String, ActivityCategoryDTO> activityCategoryDTOsMap = activityCategoryDTOs.stream()
				.collect(Collectors.toMap(ac -> ac.getName(), ac -> ac));

		activityCategoriesInRepository.stream().filter(ac -> !activityCategoryDTOsMap.containsKey(ac.getName()))
				.forEach(this::deleteActivityCategory);
	}
}
