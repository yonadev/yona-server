/*******************************************************************************
 * Copyright (c) 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.service;

import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.assertThat;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.repository.Repository;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import nu.yona.server.device.service.DeviceService;
import nu.yona.server.entities.UserRepositoriesConfiguration;
import nu.yona.server.subscriptions.service.PrivateUserDataMigrationService.MigrationStep;
import nu.yona.server.subscriptions.service.migration.AddFirstDevice;
import nu.yona.server.subscriptions.service.migration.EncryptBuddyLastStatusChangeTime;
import nu.yona.server.subscriptions.service.migration.MoveVpnPasswordToDevice;
import nu.yona.server.test.util.BaseSpringIntegrationTest;
import nu.yona.server.test.util.JUnitUtil;

@Configuration
@ComponentScan(useDefaultFilters = false, basePackages = { "nu.yona.server.subscriptions.service" }, includeFilters = {
		@ComponentScan.Filter(classes = { MigrationStep.class }, type = FilterType.ASSIGNABLE_TYPE),
		@ComponentScan.Filter(pattern = "nu.yona.server.subscriptions.service.PrivateUserDataMigrationService", type = FilterType.REGEX) }, excludeFilters = {
				@ComponentScan.Filter(pattern = ".*MockMigrationStep.*", type = FilterType.REGEX) })
class PrivateUserDataMigrationServiceIntegrationStepSequenceTestConfiguration extends UserRepositoriesConfiguration
{
}

/**
 * Test to assert the order of the defined migration steps. <br/>
 * NEVER EVER REMOVE a migration step. If a migration step becomes superfluous, then make the upgrade method empty, but retain the
 * class and its @Order annotation. New steps need to be added below, but the step variables should never be deleted.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { PrivateUserDataMigrationServiceIntegrationStepSequenceTestConfiguration.class })
public class PrivateUserDataMigrationServiceIntegration_stepOrderTest extends BaseSpringIntegrationTest
{
	@Autowired
	private PrivateUserDataMigrationService service;
	private final Field migrationStepsField = JUnitUtil.getAccessibleField(PrivateUserDataMigrationService.class,
			"migrationSteps");

	@MockBean
	private DeviceService mockDeviceService;

	@Autowired
	private EncryptBuddyLastStatusChangeTime step1;

	@Autowired
	private AddFirstDevice step2;

	@Autowired
	private MoveVpnPasswordToDevice step3;

	@Override
	protected Map<Class<?>, Repository<?, ?>> getRepositories()
	{
		return Collections.emptyMap();
	}

	@Test
	public void getSteps_allSteps_stepsReturnedInOrder() throws IllegalArgumentException, IllegalAccessException
	{
		@SuppressWarnings("unchecked")
		List<MigrationStep> migrationSteps = (List<MigrationStep>) migrationStepsField.get(service);
		assertThat(migrationSteps, contains(step1, step2, step3));
	}
}