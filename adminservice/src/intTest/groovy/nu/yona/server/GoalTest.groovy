package nu.yona.server

import groovyx.net.http.RESTClient
import spock.lang.Ignore
import spock.lang.IgnoreRest
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll
import groovy.json.*

class GoalTest extends Specification {

	def baseURL = "http://localhost:8080"

	YonaServer yonaServer = new YonaServer(baseURL)

	def 'Get all goals loaded from file'(){
		given:

		when:
			def response = yonaServer.getAllGoals()

		then:
			response.status == 200
			response.responseData._links.self.href == baseURL + yonaServer.GOALS_PATH
			response.responseData._embedded.goals.size() > 0
			
	}
}
