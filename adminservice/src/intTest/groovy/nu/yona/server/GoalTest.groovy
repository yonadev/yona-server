package nu.yona.server

import groovy.json.*

import spock.lang.Shared
import spock.lang.Specification

import nu.yona.server.YonaServer
import nu.yona.server.test.Service

class GoalTest extends Specification
{
	@Shared
	def adminServiceBaseURL = Service.getProperty('yona.adminservice.url', "http://localhost:8080")
	def YonaServer adminService = new YonaServer(adminServiceBaseURL)

	def 'Get all goals loaded from file'()
	{
		given:

		when:
			def response = adminService.getAllGoals()

		then:
			response.status == 200
			response.responseData._links.self.href == adminServiceBaseURL + adminService.GOALS_PATH
			response.responseData._embedded.goals.size() > 0
	}
}
