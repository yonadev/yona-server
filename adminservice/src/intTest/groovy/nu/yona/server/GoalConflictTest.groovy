package nu.yona.server

import groovy.json.*
import nu.yona.server.test.AbstractYonaIntegrationTest
import spock.lang.Shared

/**
 * This class will test the goal conflict handling. In order to do this first a user needs to be created. Then a goal needs to be 
 * set for the user. Then we will simulate the Smoothwall server by 
 * @author pgussow
 *
 */
class GoalConflictTest extends AbstractYonaIntegrationTest {

	@Shared
	def timestamp = YonaServer.getTimeStamp()
	def userCreationJSON = """{
				"firstName":"John",
				"lastName":"Doe ${timestamp}",
				"nickname":"JD ${timestamp}",
				"mobileNumber":"+${timestamp}",
				"devices":[
					"Galaxy mini"
				],
				"goals":[
					"gambling"
				]}"""
	def password = "John Doe"
	def user = null;
	
	def 'Setup - Get the gambling goal'(){
		given:

		when:
			def response = adminService.getAllGoals()
			response.dump();

		then:
			response.status == 200
			response.responseData._links.self.href == adminServiceBaseURL + adminService.GOALS_PATH
			response.responseData._embedded.goals.size() > 0
			response.responseData._embedded.goals.containsKey('gambling')
	}
	
	def 'Setup - Create user'() {
		when:
			def response = appService.addUser(userCreationJSON, password)
			
			//Store the user data for usage later on
			user = response.responseData;
		then:
			response.status == 201
			
	}
	
	def 'Setup - Add gambling goal'() {
		
	}
}
