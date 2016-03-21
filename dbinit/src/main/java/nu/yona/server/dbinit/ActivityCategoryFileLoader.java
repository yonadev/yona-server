/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.dbinit;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import nu.yona.server.goals.service.ActivityCategoryDTO;
import nu.yona.server.goals.service.ActivityCategoryService;

@Component
public class ActivityCategoryFileLoader implements CommandLineRunner
{
	private static final Logger logger = LoggerFactory.getLogger(ActivityCategoryFileLoader.class);

	private final String ACTIVITY_CATEGORIES_FILE = "data/activityCategories.json";

	@Autowired
	private ActivityCategoryService activityCategoryService;

	@Override
	public void run(String... args) throws Exception
	{
		loadActivityCategoriesFromFile();
	}

	private void loadActivityCategoriesFromFile()
	{
		try (InputStream input = new FileInputStream(ACTIVITY_CATEGORIES_FILE))
		{
			logger.info("Loading activity categories from file '{}' in directory '{}'", ACTIVITY_CATEGORIES_FILE,
					System.getProperty("user.dir"));
			ObjectMapper mapper = new ObjectMapper();
			Set<ActivityCategoryDTO> activityCategoriesFromFile = mapper.readValue(input,
					new TypeReference<Set<ActivityCategoryDTO>>() {
					});
			activityCategoryService.importActivityCategories(activityCategoriesFromFile);
			logger.info("Activity categories loaded successfully");
		}
		catch (IOException e)
		{
			logger.error("Error loading activity categories from file '" + ACTIVITY_CATEGORIES_FILE + "'", e);
			throw ActivityCategoryFileLoaderException.loadingActivityCategoriesFromFile(e, ACTIVITY_CATEGORIES_FILE);
		}
	}
}
