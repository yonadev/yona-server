/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import groovy.json.*
import nu.yona.server.test.AnalysisService
import spock.lang.Shared
import spock.lang.Specification

class RelevantSmoothwallCategoriesTest extends Specification
{
	@Shared
	def AnalysisService analysisService = new AnalysisService()

	def 'Get relevant Smoothwall categories'()
	{
		given:

		when:
		def response = analysisService.getRelevantSmoothwallCategories()

		then:
		response.status == 200
		response.responseData.categories.size() == 5
		response.responseData.categories.contains("Gambling")
		response.responseData.categories.contains("lotto")
		response.responseData.categories.contains("social")
		response.responseData.categories.contains("news/media")
		response.responseData.categories.contains("newsgroups/forums")
	}
}
