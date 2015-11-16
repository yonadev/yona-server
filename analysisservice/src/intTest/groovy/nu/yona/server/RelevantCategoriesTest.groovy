package nu.yona.server

import groovyx.net.http.RESTClient
import spock.lang.Ignore
import spock.lang.IgnoreRest
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll
import groovy.json.*

class InitialGoalCreationTest extends Specification {

	def baseURL = "http://localhost:8081"

	YonaServer yonaServer = new YonaServer(baseURL)

	def 'Get relevant categories'(){
		given:

		when:
			def response = yonaServer.getRelevantCategories()

		then:
			response.status == 200
			response.responseData.categories.size() == 4
			response.responseData.categories[0] == "Java"
			response.responseData.categories[1] == "C++"
			response.responseData.categories[2] == "poker"
			response.responseData.categories[3] == "lotto"
			
	}
}
