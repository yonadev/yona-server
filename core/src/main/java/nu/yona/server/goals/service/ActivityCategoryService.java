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
	/**
	 * The FilterService is created as a small nested class, to ensure ActivityCategoryService.getAllActivityCategories is called
	 * from outside, through the Spring proxy. Calling it from inside means the call does not go through the Spring proxy and thus
	 * Spring cannot intercept the call to fetch the data from the cache.
	 */
	@Service
	public static class FilterService
	{
		@Autowired
		private ActivityCategoryService activityCategoryService;

		public Set<ActivityCategoryDto> getMatchingCategoriesForSmoothwallCategories(Set<String> smoothwallCategories)
		{
			return activityCategoryService.getAllActivityCategories().stream().filter(ac -> {
				Set<String> acSmoothwallCategories = new HashSet<>(ac.getSmoothwallCategories());
				acSmoothwallCategories.retainAll(smoothwallCategories);
				return !acSmoothwallCategories.isEmpty();
			}).collect(Collectors.toSet());
		}

		public Set<ActivityCategoryDto> getMatchingCategoriesForApp(String application)
		{
			return activityCategoryService.getAllActivityCategories().stream()
					.filter(ac -> ac.getApplications().contains(application)).collect(Collectors.toSet());
		}
	}

	@Autowired
	private ActivityCategoryRepository repository;

	private static final Logger logger = LoggerFactory.getLogger(ActivityCategoryService.class);

	@Transactional
	public ActivityCategoryDto getActivityCategory(UUID id)
	{
		ActivityCategory activityCategoryEntity = getEntityById(id);
		return ActivityCategoryDto.createInstance(activityCategoryEntity);
	}

	@Cacheable(value = "activityCategorySet", key = "'instance'")
	@Transactional
	public Set<ActivityCategoryDto> getAllActivityCategories()
	{
		// Sort the collection to make it simpler to compare the activity categories
		return StreamSupport.stream(repository.findAll().spliterator(), false).map(e -> ActivityCategoryDto.createInstance(e))
				.collect(Collectors.toCollection(() -> new TreeSet<>(
						(Comparator<ActivityCategoryDto> & Serializable) (l, r) -> l.getName().compareTo(r.getName()))));
	}

	@CacheEvict(value = "activityCategorySet", key = "'instance'")
	@Transactional
	public ActivityCategoryDto addActivityCategory(ActivityCategoryDto activityCategoryDto)
	{
		logger.info("Adding activity category '{}' with ID '{}'", activityCategoryDto.getName(Translator.EN_US_LOCALE),
				activityCategoryDto.getId());
		verifyNoDuplicateNames(Collections.emptySet(), activityCategoryDto.getLocalizableNameByLocale());
		return ActivityCategoryDto.createInstance(repository.save(activityCategoryDto.createActivityCategoryEntity()));
	}

	@CacheEvict(value = "activityCategorySet", key = "'instance'")
	@Transactional
	public ActivityCategoryDto updateActivityCategory(UUID id, ActivityCategoryDto activityCategoryDto)
	{
		ActivityCategory originalEntity = getEntityById(id);
		logger.info("Updating activity category '{}' with ID '{}'", getName(originalEntity), id);
		verifyNoDuplicateNames(Collections.singleton(id), activityCategoryDto.getLocalizableNameByLocale());
		return ActivityCategoryDto.createInstance(updateActivityCategory(originalEntity, activityCategoryDto));
	}

	@CacheEvict(value = "activityCategorySet", key = "'instance'")
	@Transactional
	public void updateActivityCategorySet(Set<ActivityCategoryDto> activityCategoryDtos)
	{
		logger.info("Updating entire activity category set");
		Set<ActivityCategory> activityCategoriesInRepository = getAllActivityCategoryEntities();
		deleteRemovedActivityCategories(activityCategoriesInRepository, activityCategoryDtos);
		explicitlyFlushUpdatesToDatabase();
		addOrUpdateNewActivityCategories(activityCategoryDtos, activityCategoriesInRepository);
		logger.info("Activity category set update completed");
	}

	@CacheEvict(value = "activityCategorySet", key = "'instance'")
	@Transactional
	public void deleteActivityCategory(UUID id)
	{
		deleteActivityCategory(getEntityById(id));
	}

	private void deleteActivityCategory(ActivityCategory entity)
	{
		logger.info("Deleting activity category '{}' with ID '{}'", getName(entity), entity.getId());
		repository.delete(entity);
	}

	private void verifyNoDuplicateNames(Set<UUID> idsToSkip, Map<Locale, String> localizableName)
	{
		Iterable<ActivityCategory> allCategories = repository.findAll();
		List<ActivityCategory> categoriesToConsider = StreamSupport.stream(allCategories.spliterator(), false)
				.filter(c -> !idsToSkip.contains(c.getId())).collect(Collectors.toList());
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
			ActivityCategoryDto activityCategorySourceDto)
	{
		return repository.save(activityCategorySourceDto.updateActivityCategory(activityCategoryTargetEntity));
	}

	private ActivityCategory getEntityById(UUID id)
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

	private void addOrUpdateNewActivityCategories(Set<ActivityCategoryDto> activityCategoryDtos,
			Set<ActivityCategory> activityCategoriesInRepository)
	{
		Map<UUID, ActivityCategory> activityCategoriesInRepositoryMap = activityCategoriesInRepository.stream()
				.collect(Collectors.toMap(ac -> ac.getId(), ac -> ac));

		for (ActivityCategoryDto activityCategoryDto : activityCategoryDtos)
		{
			ActivityCategory activityCategoryEntity = activityCategoriesInRepositoryMap.get(activityCategoryDto.getId());
			if (activityCategoryEntity == null)
			{
				addActivityCategory(activityCategoryDto);
				explicitlyFlushUpdatesToDatabase();
			}
			else
			{
				if (!isUpToDate(activityCategoryEntity, activityCategoryDto))
				{
					updateActivityCategory(activityCategoryDto.getId(), activityCategoryDto);
					explicitlyFlushUpdatesToDatabase();
				}
			}
		}
	}

	private boolean isUpToDate(ActivityCategory entity, ActivityCategoryDto dto)
	{
		return entity.getLocalizableName().equals(dto.getLocalizableNameByLocale())
				&& entity.isMandatoryNoGo() == dto.isMandatoryNoGo()
				&& entity.getSmoothwallCategories().equals(dto.getSmoothwallCategories())
				&& entity.getApplications().equals(dto.getApplications())
				&& entity.getLocalizableDescription().equals(dto.getLocalizableDescriptionByLocale());
	}

	private void deleteRemovedActivityCategories(Set<ActivityCategory> activityCategoriesInRepository,
			Set<ActivityCategoryDto> activityCategoryDtos)
	{
		Map<UUID, ActivityCategoryDto> activityCategoryDtosMap = activityCategoryDtos.stream()
				.collect(Collectors.toMap(ac -> ac.getId(), ac -> ac));

		activityCategoriesInRepository.stream().filter(ac -> !activityCategoryDtosMap.containsKey(ac.getId()))
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
