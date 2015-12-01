package nu.yona.server

import groovy.json.*
import spock.lang.Specification

class GoalTest extends Specification {

	def baseURL = System.getProperty("yona.adminservice.url", "http://localhost:8080")

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
