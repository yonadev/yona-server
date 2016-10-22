/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.service;

import java.io.Serializable;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
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
		// Sort the collection to make it simpler to compare the activity categories
		return StreamSupport.stream(repository.findAll().spliterator(), false).map(e -> ActivityCategoryDTO.createInstance(e))
				.collect(Collectors.toCollection(() -> new TreeSet<>(
						(Comparator<ActivityCategoryDTO> & Serializable) (l, r) -> l.getName().compareTo(r.getName()))));
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
		ActivityCategory originalEntity = getEntityByID(id);
		logger.info("Updating activity category '{}' with ID '{}'", getName(originalEntity), id);
		verifyNoDuplicateNames(Collections.singleton(id), activityCategoryDTO.getLocalizableNameByLocale());
		return ActivityCategoryDTO.createInstance(updateActivityCategory(originalEntity, activityCategoryDTO));
	}

	@CacheEvict(value = "activityCategorySet", key = "'instance'")
	@Transactional
	public void updateActivityCategorySet(Set<ActivityCategoryDTO> activityCategoryDTOs)
	{
		logger.info("Updating entire activity category set");
		Set<ActivityCategory> activityCategoriesInRepository = getAllActivityCategoryEntities();
		deleteRemovedActivityCategories(activityCategoriesInRepository, activityCategoryDTOs);
		explicitlyFlushUpdatesToDatabase();
		addOrUpdateNewActivityCategories(activityCategoryDTOs, activityCategoriesInRepository);
		logger.info("Activity category set update completed");
	}

	@CacheEvict(value = "activityCategorySet", key = "'instance'")
	@Transactional
	public void deleteActivityCategory(UUID id)
	{
		deleteActivityCategory(getEntityByID(id));
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

	private void deleteActivityCategory(ActivityCategory entity)
	{
		logger.info("Deleting activity category '{}' with ID '{}'", getName(entity), entity.getID());
		repository.delete(entity);
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
		if (categoriesToConsider.stream()
				.anyMatch(c -> localeAndName.getValue().equals(c.getLocalizableName().get(localeAndName.getKey()))))
		{
			throw ActivityCategoryException.duplicateName(localeAndName.getKey(), localeAndName.getValue());
		}
	}

	private ActivityCategory updateActivityCategory(ActivityCategory activityCategoryTargetEntity,
			ActivityCategoryDTO activityCategorySourceDTO)
	{
		return repository.save(activityCategorySourceDTO.updateActivityCategory(activityCategoryTargetEntity));
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

	private void addOrUpdateNewActivityCategories(Set<ActivityCategoryDTO> activityCategoryDTOs,
			Set<ActivityCategory> activityCategoriesInRepository)
	{
		Map<UUID, ActivityCategory> activityCategoriesInRepositoryMap = activityCategoriesInRepository.stream()
				.collect(Collectors.toMap(ac -> ac.getID(), ac -> ac));

		for (ActivityCategoryDTO activityCategoryDTO : activityCategoryDTOs)
		{
			ActivityCategory activityCategoryEntity = activityCategoriesInRepositoryMap.get(activityCategoryDTO.getID());
			if (activityCategoryEntity == null)
			{
				addActivityCategory(activityCategoryDTO);
				explicitlyFlushUpdatesToDatabase();
			}
			else
			{
				if (!isUpToDate(activityCategoryEntity, activityCategoryDTO))
				{
					updateActivityCategory(activityCategoryDTO.getID(), activityCategoryDTO);
					explicitlyFlushUpdatesToDatabase();
				}
			}
		}
	}

	private boolean isUpToDate(ActivityCategory entity, ActivityCategoryDTO dto)
	{
		return entity.getLocalizableName().equals(dto.getLocalizableNameByLocale())
				&& entity.isMandatoryNoGo() == dto.isMandatoryNoGo()
				&& entity.getSmoothwallCategories().equals(dto.getSmoothwallCategories())
				&& entity.getApplications().equals(dto.getApplications())
				&& entity.getLocalizableDescription().equals(dto.getLocalizableDescriptionByLocale());
	}

	private void deleteRemovedActivityCategories(Set<ActivityCategory> activityCategoriesInRepository,
			Set<ActivityCategoryDTO> activityCategoryDTOs)
	{
		Map<UUID, ActivityCategoryDTO> activityCategoryDTOsMap = activityCategoryDTOs.stream()
				.collect(Collectors.toMap(ac -> ac.getID(), ac -> ac));

		activityCategoriesInRepository.stream().filter(ac -> !activityCategoryDTOsMap.containsKey(ac.getID()))
				.forEach(ac -> deleteActivityCategory(ac));
	}

	private String getName(ActivityCategory entity)
	{
		return entity.getLocalizableName().get(Translator.EN_US_LOCALE);
	}

	/**
	 * This method calls flush on the activity category repository. It shouldn't be necessary to do this, but the implicit flush
	 * (done just before JPA sends a query to the database) causes a
	 * "java.sql.SQLException: invalid transaction state: read-only SQL-transaction". Explicit flushes prevent that issue. This
	 * requires the repository to be a JpaRepository rather than a CrudRepository.
	 */
	private void explicitlyFlushUpdatesToDatabase()
	{
		repository.flush();
	}
}
