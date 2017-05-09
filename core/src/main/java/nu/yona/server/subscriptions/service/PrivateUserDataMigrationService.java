/*******************************************************************************
 * Copyright (c) 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import java.util.Arrays;
import java.util.List;

import javax.transaction.Transactional;

import org.springframework.stereotype.Service;

import nu.yona.server.exceptions.YonaException;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.service.migration.EncryptBuddyLastStatusChangeTime;

@Service
public class PrivateUserDataMigrationService
{
	@FunctionalInterface
	public interface MigrationStep
	{
		void upgrade(User userEntity);
	}

	private static List<Class<? extends MigrationStep>> migrationSteps = Arrays.asList(EncryptBuddyLastStatusChangeTime.class);

	@Transactional
	void upgrade(User userEntity)
	{
		int originalVersion = userEntity.getPrivateDataMigrationVersion();
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

	private static MigrationStep getMigrationStepInstance(int fromVersion)
	{
		Class<? extends MigrationStep> migrationStep = migrationSteps.get(fromVersion);
		try
		{
			return migrationStep.newInstance();
		}
		catch (InstantiationException | IllegalAccessException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	public static int getCurrentVersion()
	{
		return migrationSteps.size();
	}

	/*
	 * For unit testing purposes only.
	 */
	public static List<Class<? extends MigrationStep>> getMigrationSteps()
	{
		return migrationSteps;
	}

	/*
	 * For unit testing purposes only.
	 */
	public static void setMigrationSteps(List<Class<? extends MigrationStep>> value)
	{
		migrationSteps = value;
	}
}