package nu.yona.server

import groovyx.net.http.RESTClient
import spock.lang.Ignore
import spock.lang.IgnoreRest
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll
import groovy.json.*

class InitialGoalCreationTest extends Specification {

	def baseURL = "http://localhost:8080"

	YonaServer yonaServer = new YonaServer(baseURL)

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

		then:
			response.responseData._links.self.href.startsWith(baseURL + yonaServer.GOALS_PATH)
	}

	def 'Get all goals'(){
		given:

		when:
			def goals = yonaServer.getAllGoals()

		then:
			goals._links.self.href == baseURL + yonaServer.GOALS_PATH
			goals._embedded.Goals.size() == 2
			
	}
}
