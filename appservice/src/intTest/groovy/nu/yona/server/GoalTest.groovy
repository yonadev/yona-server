package nu.yona.server

import groovy.json.*

class GoalTest extends AbstractAppServiceIntegrationTest
{
	def 'Get all goals'()
	{
		given:

		when:
			def response = appService.getAllGoals()

		then:
			response.status == 200
			response.responseData._links.self.href == appService.url + appService.GOALS_PATH
			response.responseData._embedded.goals.size() > 0
			
	}
}
