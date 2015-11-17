package nu.yona.server

import groovyx.net.http.RESTClient
import groovyx.net.http.HttpResponseException
import spock.lang.Ignore
import spock.lang.IgnoreRest
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll
import groovy.json.*

class InitialGoalCreationTest extends Specification {

	def baseURL = "http://localhost:8080"

	YonaServer yonaServer = new YonaServer(baseURL)

	@Shared
	def initialGoalsCount
	@Shared
	def gamblingURL
	@Shared
	def programmingURL

	def 'Get all goals'(){
		given:

		when:
			def response = yonaServer.getAllGoals()
			initialGoalsCount = response.responseData._embedded.goals.size()

		then:
			response.status == 200
			response.responseData._links.self.href == baseURL + yonaServer.GOALS_PATH
			
	}

	def 'Add goal Gambling'(){
		given:

		when:
			def response = yonaServer.addGoal("""{
				"name":"gambling",
				"categories":[
					"poker",
					"lotto"
				]
			}""")
			gamblingURL = response.responseData._links.self.href

		then:
			response.status == 201
			response.responseData._links.self.href.startsWith(baseURL + yonaServer.GOALS_PATH)
	}

	def 'Add goal Programming'(){
		given:

		when:
			def response = yonaServer.addGoal("""{
				"name":"programming",
				"categories":[
					"Java",
					"C++"
				]
			}""")
			programmingURL = response.responseData._links.self.href

		then:
			response.status == 201
			response.responseData._links.self.href.startsWith(baseURL + yonaServer.GOALS_PATH)
	}

	def 'Get all goals'(){
		given:

		when:
			def response = yonaServer.getAllGoals()

		then:
			response.status == 200
			response.responseData._links.self.href == baseURL + yonaServer.GOALS_PATH
			response.responseData._embedded.goals.size() == initialGoalsCount + 2
			
	}
	
	def 'Delete goal Gambling'(){
		given:

		when:
			def response = yonaServer.deleteGoal(gamblingURL)

		then:
			response.status == 200
			verifyGoalDoesNotExist(gamblingURL)
	}
	
	def 'Delete goal Programming'(){
		given:

		when:
			def response = yonaServer.deleteGoal(programmingURL)

		then:
			response.status == 200
			verifyGoalDoesNotExist(programmingURL)
	}
	
	void verifyGoalDoesNotExist(goalURL)
	{
		try {
			def response = yonaServer.getGoal(goalURL)
			assert false;
		} catch (HttpResponseException e) {
			assert e.statusCode == 404
		}
	}
}
