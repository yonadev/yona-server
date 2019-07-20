/*******************************************************************************
 * Copyright (c) 2015, 2019 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import static nu.yona.server.test.CommonAssertions.*

import groovy.json.*

class ActivityCategoriesTest extends AbstractAppServiceIntegrationTest
{
	def 'Get all activity categories'()
	{
		given:

		when:
		def response = appService.getAllActivityCategoriesWithLanguage("en-US")

		then:
		assertResponseStatusOk(response)
		response.responseData._links.self.href == appService.url + appService.ACTIVITY_CATEGORIES_PATH
		response.responseData._embedded."yona:activityCategories".size() > 0
		def gamblingCategory = response.responseData._embedded."yona:activityCategories".find
		{
			it._links.self.href == GAMBLING_ACT_CAT_URL
		}
		gamblingCategory.keySet() == ["_links", "name", "description"] as Set
		gamblingCategory.name == "Gambling"
		gamblingCategory.description == "This challenge includes apps and sites like Poker and Blackjack"
	}
}
