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
	def johnDoeURL
	def johnDoeVPNLoginID
	
	def 'Setup - Get the gambling goal'(){
		given:

		when:
			def response = adminService.getAllGoals()

		then:
			response.status == 200
			response.responseData._links.self.href == adminServiceBaseURL + adminService.GOALS_PATH
			response.responseData._embedded.goals.size() > 0
			response.responseData._embedded.goals.gambling != null
	}
	
	def 'Setup - Create user'() {
		when:
			def response = appService.addUser(userCreationJSON, password)
			
			if (response.status == 201)
			{
				johnDoeURL = appService.stripQueryString(response.responseData._links.self.href)
				johnDoeVPNLoginID = response.responseData.vpnProfile.vpnLoginID;
	
				//Store the user data for usage later on
				user = response.responseData;
			}
		then:
			response.status == 201
			johnDoeURL?.trim()
			johnDoeVPNLoginID?.trim()
			
	}
	
	def 'Setup - Add stuff to analysis engine'() {
		given :
			def startTime = new Date();
		when:
			def response1 = analysisService.postToAnalysisEngine("""{
					"vpnLoginID":"${johnDoeVPNLoginID}",
					"categories": ["gambling"],
					"url":"http://www.poker.com/1"
					}""")
			Thread.sleep(30000);
			def response2 = analysisService.postToAnalysisEngine("""{
					"vpnLoginID":"${johnDoeVPNLoginID}",
					"categories": ["gambling"],
					"url":"http://www.poker.com/2"
					}""")
		then:
			response1.status == 200
			response2.status == 200
	}
	
	def 'Check - Get conflict message'() {
		when:
			def response = appService.getAnonymousMessages(johnDoeURL, password)
			if(response.status == 200 && response.responseData._embedded.goalConflictMessages) {
				richardQuinGoalConflictMessage1URL = response.responseData._embedded.goalConflictMessages[0]._links.self.href
			}
		then:
			response.status == 200
			response.responseData._embedded.goalConflictMessages.size() > 0
			response.responseData._embedded.goalConflictMessages[0].hasProperty('endTime')
	}
}
