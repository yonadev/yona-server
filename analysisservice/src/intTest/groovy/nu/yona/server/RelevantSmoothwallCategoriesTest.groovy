/*******************************************************************************
 * Copyright (c) 2022 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import static nu.yona.server.test.CommonAssertions.assertResponseStatusOk

import nu.yona.server.test.AnalysisService
import spock.lang.Shared
import spock.lang.Specification

class RelevantSmoothwallCategoriesTest extends Specification
{
	@Shared
	AnalysisService analysisService = new AnalysisService()

	def 'Get relevant Smoothwall categories'()
	{
		when:
		def response = analysisService.getRelevantSmoothwallCategories()

		then:
		assertResponseStatusOk(response)
		response.json.categories.size() > 10
		response.json.categories.contains("Gambling")
		response.json.categories.contains("lotto")
		response.json.categories.contains("news/media")
		response.json.categories.contains("newsgroups/forums")
		response.json.categories.contains("social")
	}
}
