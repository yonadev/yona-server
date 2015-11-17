package nu.yona.server

import groovyx.net.http.RESTClient
import spock.lang.Ignore
import spock.lang.IgnoreRest
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll
import groovy.json.*

class RelevantCategoriesTest extends Specification {

	def baseURL = "http://localhost:8081"

	YonaServer yonaServer = new YonaServer(baseURL)

	def 'Get relevant categories'(){
		given:

		when:
			def response = yonaServer.getRelevantCategories()

		then:
			response.status == 200
			response.responseData.categories.size() == 4
			response.responseData.categories.contains("pornography")
			response.responseData.categories.contains("nudity")
			response.responseData.categories.contains("news/media")
			response.responseData.categories.contains("newsgroups/forums")
			
	}
}
