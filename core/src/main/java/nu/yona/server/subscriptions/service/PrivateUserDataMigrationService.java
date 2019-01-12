/*******************************************************************************
 * Copyright (c) 2017, 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.List;

import javax.annotation.PostConstruct;
import javax.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import nu.yona.server.subscriptions.entities.User;

@Service
public class PrivateUserDataMigrationService
{
	@FunctionalInterface
	public interface MigrationStep
	{
		void upgrade(User userEntity);
	}

	private static final Logger logger = LoggerFactory.getLogger(PrivateUserDataMigrationService.class);

	@Autowired
	private List<MigrationStep> migrationSteps;

	@PostConstruct
	private void onAfterInit()
	{
		User.setCurrentPrivateDataMigrationVersion(migrationSteps.size());
	}

	@Transactional
	void upgrade(User userEntity)
	{
		int originalVersion = userEntity.getPrivateDataMigrationVersion();
		logger.info("Migrating private user data of user with ID {} from {} to {}", userEntity.getId(), originalVersion,
				getCurrentVersion());
		for (int fromVersion = originalVersion; fromVersion < getCurrentVersion(); fromVersion++)
		{
			getMigrationStepInstance(fromVersion).upgrade(userEntity);
			userEntity.setPrivateDataMigrationVersion(fromVersion + 1);
		}
	}

	boolean isUpToDate(User userEntity)
	{
		return userEntity.getPrivateDataMigrationVersion() == getCurrentVersion();
	}

	private MigrationStep getMigrationStepInstance(int fromVersion)
	{
		return migrationSteps.get(fromVersion);
	}

	public int getCurrentVersion()
	{
		return migrationSteps.size();
	}
}