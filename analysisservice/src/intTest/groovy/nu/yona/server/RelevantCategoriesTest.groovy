package nu.yona.server

import groovy.json.*

import spock.lang.Shared
import spock.lang.Specification

import nu.yona.server.test.AnalysisService

class RelevantCategoriesTest extends Specification
{
	@Shared
	def AnalysisService analysisService = new AnalysisService()

	def 'Get relevant categories'()
	{
		given:

		when:
			def response = analysisService.getRelevantCategories()

		then:
			response.status == 200
			response.responseData.categories.size() == 4
			response.responseData.categories.contains("Gambling")
			response.responseData.categories.contains("lotto")
			response.responseData.categories.contains("news/media")
			response.responseData.categories.contains("newsgroups/forums")
			
	}
}
