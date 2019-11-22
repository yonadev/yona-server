/*******************************************************************************
 * Copyright (c) 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import nu.yona.server.properties.YonaProperties;

public class PropertiesValidator implements Validator
{
	private static final Logger logger = LoggerFactory.getLogger(PropertiesValidator.class);

	@Override
	public boolean supports(Class<?> type)
	{
		return type == YonaProperties.class;
	}

	@Override
	public void validate(Object o, Errors errors)
	{
		YonaProperties properties = (YonaProperties) o;
		// Check one critical property to prevent from accidentally enabling the test server setting on production
		if (properties.isTestServer() && properties.getSms().isEnabled())
		{
			errors.rejectValue("testServer", "properties.inconsistent",
					"Seems like yona.testServer property is set on a production server, as SMS is enabled");
		}
		if (properties.isEnableHibernateStatsAllowed() && !properties.isTestServer())
		{
			errors.rejectValue("enableHibernateStatsAllowed", "properties.inconsistent",
					"This property can only be enabled on a test server");
		}
	}

}