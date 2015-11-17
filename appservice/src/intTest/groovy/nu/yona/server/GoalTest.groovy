package nu.yona.server

import groovy.json.*
import spock.lang.Specification

class GoalTest extends Specification {

	def appServiceBaseURL = System.properties.'yona.appservice.url'
	def YonaServer appService = new YonaServer(appServiceBaseURL)

	def 'Get all goals'(){
		given:

		when:
			def response = appService.getAllGoals()

		then:
			response.status == 200
			response.responseData._links.self.href == appServiceBaseURL + appService.GOALS_PATH
			response.responseData._embedded.goals.size() == 2
			
	}
}
