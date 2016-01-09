/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import groovy.json.*

class ActivityCategoriesTest extends AbstractAppServiceIntegrationTest
{
	def 'Get all activity categories'()
	{
		given:

		when:
		def response = appService.getAllActivityCategories()

		then:
		response.status == 200
		response.responseData._links.self.href == appService.url + appService.ACTIVITY_CATEGORIES_PATH
		response.responseData._embedded.activityCategories.size() > 0
	}
}
