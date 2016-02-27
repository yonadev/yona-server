/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/

package nu.yona.server

import groovy.json.*
import spock.lang.Shared
import spock.lang.Specification

class ActivityCategoriesTest extends Specification
{
	@Shared
	def AdminService adminService = new AdminService()

	def 'Get all activity categories loaded from file'()
	{
		given:

		when:
		def response = adminService.getAllActivityCategories()

		then:
		response.status == 200
		response.responseData._links.self.href == adminService.url + AdminService.ACTIVITY_CATEGORIES_PATH
		response.responseData._embedded.activityCategories.size() > 0
	}
}
