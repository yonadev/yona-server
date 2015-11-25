package nu.yona.server

import groovy.json.*
import spock.lang.Specification

class RelevantCategoriesTest extends Specification {

	def baseURL = System.getProperty("yona.analysisservice.url", "http://localhost:8081")

	YonaServer yonaServer = new YonaServer(baseURL)

	def 'Get relevant categories'(){
		given:

		when:
			def response = yonaServer.getRelevantCategories()

		then:
			response.status == 200
			response.responseData.categories.size() == 4
			response.responseData.categories.contains("Gambling")
			response.responseData.categories.contains("lotto")
			response.responseData.categories.contains("news/media")
			response.responseData.categories.contains("newsgroups/forums")
			
	}
}
