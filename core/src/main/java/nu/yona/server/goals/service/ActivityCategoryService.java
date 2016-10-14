/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.service;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import nu.yona.server.Translator;
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
		logger.info("Adding activity category '{}' with ID '{}'", activityCategoryDTO.getName(Translator.EN_US_LOCALE),
				activityCategoryDTO.getID());
		verifyNoDuplicateNames(Collections.emptySet(), activityCategoryDTO.getLocalizableNameByLocale());
		return ActivityCategoryDTO.createInstance(repository.save(activityCategoryDTO.createActivityCategoryEntity()));
	}

	@CacheEvict(value = "activityCategorySet", key = "'instance'")
	@Transactional
	public ActivityCategoryDTO updateActivityCategory(UUID id, ActivityCategoryDTO activityCategoryDTO)
	{
		logger.info("Updating activity category '{}' with ID '{}'", activityCategoryDTO.getName(Translator.EN_US_LOCALE), id);
		verifyNoDuplicateNames(Collections.singleton(id), activityCategoryDTO.getLocalizableNameByLocale());
		ActivityCategory originalEntity = getEntityByID(id);
		return ActivityCategoryDTO.createInstance(updateActivityCategory(originalEntity, activityCategoryDTO));
	}

	private void verifyNoDuplicateNames(Set<UUID> idsToSkip, Map<Locale, String> localizableName)
	{
		Iterable<ActivityCategory> allCategories = repository.findAll();
		List<ActivityCategory> categoriesToConsider = StreamSupport.stream(allCategories.spliterator(), false)
				.filter(c -> !idsToSkip.contains(c.getID())).collect(Collectors.toList());
		for (Entry<Locale, String> localeAndName : localizableName.entrySet())
		{
			verifyNoDuplicateNames(categoriesToConsider, localeAndName);
		}
	}

	private void verifyNoDuplicateNames(List<ActivityCategory> categoriesToConsider, Entry<Locale, String> localeAndName)
	{
		if (categoriesToConsider.stream().anyMatch(c -> localeAndName.getValue().equals(c.getName().get(localeAndName.getKey()))))
		{
			throw ActivityCategoryException.duplicateName(localeAndName.getKey(), localeAndName.getValue());
		}
	}

	private ActivityCategory updateActivityCategory(ActivityCategory activityCategoryTargetEntity,
			ActivityCategoryDTO activityCategorySourceDTO)
	{
		logger.info("Updating activity category '{}'", activityCategorySourceDTO.getName(Translator.EN_US_LOCALE));
		return repository.save(activityCategorySourceDTO.updateActivityCategory(activityCategoryTargetEntity));
	}

	@CacheEvict(value = "activityCategorySet", key = "'instance'")
	@Transactional
	public void deleteActivityCategory(UUID id)
	{
		logger.info("Deleting activity category with ID '{}'", id);
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
			throw ActivityCategoryException.notFound(id);
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
