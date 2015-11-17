package nu.yona.server

import groovyx.net.http.RESTClient
import spock.lang.Ignore
import spock.lang.IgnoreRest
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll
import groovy.json.*

class BasicBuddyTest extends Specification {

	def adminServiceBaseURL = System.properties.'yona.adminservice.url'
	def YonaServer adminService = new YonaServer(adminServiceBaseURL)

	def analysisServiceBaseURL = System.properties.'yona.analysisservice.url'
	def YonaServer analysisService = new YonaServer(analysisServiceBaseURL)

	def appServiceBaseURL = System.properties.'yona.appservice.url'
	def YonaServer appService = new YonaServer(appServiceBaseURL)

	@Shared
	def richardQuinPassword = "R i c h a r d"
	def bobDunnPassword = "B o b"
	@Shared
	def richardQuinURL
	@Shared
	def richardQuinLoginID
	@Shared
	def bobDunnURL
	@Shared
	def bobDunnLoginID
	@Shared
	def richardQuinBobBuddyURL
	@Shared
	def bobDunnBuddyMessageAcceptURL
	@Shared
	def richardQuinBuddyMessageProcessURL 

	def 'Add user Richard Quin'(){
		given:

		when:
			def response = appService.addUser("""{
				"firstName":"Richard",
				"lastName":"Quin",
				"nickName":"RQ",
				"emailAddress":"rich@quin.net",
				"mobileNumber":"+12345678",
				"devices":[
					"Nexus 6"
				],
				"goals":[
					"gambling"
				]
			}""", richardQuinPassword)
			richardQuinURL = appService.stripQueryString(response.responseData._links.self.href)
			richardQuinLoginID = response.responseData.vpnProfile.loginID;

		then:
			response.status == 201
			richardQuinURL.startsWith(appServiceBaseURL + appService.USERS_PATH)

		cleanup:
			println "URL Richard: " + richardQuinURL
	}

	def 'Add user Bob Dunn'(){
		given:

		when:
			def response = appService.addUser("""{
				"firstName":"Bob",
				"lastName":"Dunn",
				"nickName":"BD",
				"emailAddress":"bob@dunn.net",
				"mobileNumber":"+13456789",
				"devices":[
					"iPhone 6"
				],
				"goals":[
					"programming"
				]
			}""", bobDunnPassword)
			bobDunnURL = appService.stripQueryString(response.responseData._links.self.href)
			bobDunnLoginID = response.responseData.vpnProfile.loginID;

		then:
			response.status == 201
			bobDunnURL.startsWith(appServiceBaseURL + appService.USERS_PATH)

		cleanup:
			println "URL Bob: " + bobDunnURL
	}

	def 'Richard requests Bob to become his buddy'(){
		given:

		when:
			def response = appService.requestBuddy(richardQuinURL, """{
				"_embedded":{
					"user":{
						"firstName":"Bob",
						"lastName":"Dunn",
						"emailAddress":"bob@dunn.net",
						"mobileNumber":"+13456789"
					}
				},
				"message":"Would you like to be my buddy?"
			}""", richardQuinPassword)
			richardQuinBobBuddyURL = response.responseData._links.self.href

		then:
			response.status == 201
			response.responseData._embedded.user.firstName == "Bob"
			richardQuinBobBuddyURL.startsWith(richardQuinURL)

		cleanup:
			println "URL buddy Richard: " + richardQuinBobBuddyURL
	}

	def 'Bob checks his direct messages'(){
		given:

		when:
			def response = appService.getDirectMessages(bobDunnURL, bobDunnPassword)
			bobDunnBuddyMessageAcceptURL = response.responseData._embedded.buddyConnectRequestMessages[0]._links.accept.href

		then:
			response.status == 200
			response.responseData._links.self.href == bobDunnURL + appService.DIRECT_MESSAGE_PATH_FRAGMENT
			response.responseData._embedded.buddyConnectRequestMessages[0].requestingUser.firstName == "Richard"
			response.responseData._embedded.buddyConnectRequestMessages[0]._links.self.href.startsWith(response.responseData._links.self.href)
			bobDunnBuddyMessageAcceptURL.startsWith(response.responseData._embedded.buddyConnectRequestMessages[0]._links.self.href)
	}

	def 'Bob accepts Richard\'s buddy request'(){
		given:

		when:
			def response = appService.postMessageActionWithPassword(bobDunnBuddyMessageAcceptURL, """{
				"properties":{
					"message":"Yes, great idea!"
				}
			}""", bobDunnPassword)

		then:
			response.status == 200
			response.responseData.properties.status == "done"
	}

	def 'Richard checks his direct messages'(){
		given:

		when:
			def response = appService.getDirectMessages(richardQuinURL, richardQuinPassword)
			richardQuinBuddyMessageProcessURL = response.responseData._embedded.buddyConnectResponseMessages[0]._links.process.href

		then:
			response.status == 200
			response.responseData._links.self.href == richardQuinURL + appService.DIRECT_MESSAGE_PATH_FRAGMENT
			response.responseData._embedded.buddyConnectResponseMessages[0].respondingUser.firstName == "Bob"
			response.responseData._embedded.buddyConnectResponseMessages[0]._links.self.href.startsWith(response.responseData._links.self.href)
			richardQuinBuddyMessageProcessURL.startsWith(response.responseData._embedded.buddyConnectResponseMessages[0]._links.self.href) 
	}

	def 'Richard processes Bob\'s buddy acceptance'(){
		given:

		when:
			def response = appService.postMessageActionWithPassword(richardQuinBuddyMessageProcessURL, """{
				"properties":{
				}
			}""", richardQuinPassword)

		then:
			response.status == 200
			response.responseData.properties.status == "done"
	}
	
	def 'Richard checks his buddy list and will find Bob there'(){
		given:

		when:
			def response = appService.getBuddies(richardQuinURL, richardQuinPassword);

		then:
			response.status == 200
			response.responseData._embedded.buddies.size() == 1
			response.responseData._embedded.buddies[0]._embedded.user.firstName == "Bob"
			response.responseData.properties.status == "done"
	}
	
	def 'Bob checks his buddy list and will find Richard there'(){
		given:

		when:
			def response = appService.getBuddies(bobDunnURL, bobDunnPassword);

		then:
			response.status == 200
			response.responseData._embedded.buddies.size() == 1
			response.responseData._embedded.buddies[0]._embedded.user.firstName == "Richard"
			response.responseData.properties.status == "done"
	}
	
	def 'When Richard would retrieve his user, Bob would be embedded as buddy'(){
		given:

		when:
			def response = appService.getUser(richardQuinURL, true, richardQuinPassword)

		then:
			response.status == 200
			response.responseData._embedded.buddies != null
			response.responseData._embedded.buddies.size() == 1
			response.responseData._embedded.buddies[0]._embedded.user.firstName == "Bob"
	}

	def 'Richard checks he has no anonymous messages'(){
		given:

		when:
			def response = appService.getAnonymousMessages(richardQuinURL, richardQuinPassword)

		then:
			response.status == 200
			response.responseData._embedded == null
	}

	def 'Bob checks he has no anonymous messages'(){
		given:

		when:
			def response = appService.getAnonymousMessages(bobDunnURL, bobDunnPassword)

		then:
			response.status == 200
			response.responseData._embedded == null
	}

	def 'Classification engine detects a potential conflict for Richard'(){
		given:

		when:
			def response = analysisService.postToAnalysisEngine("""{
			"accessorID":"${richardQuinLoginID}",
			"categories": ["poker"],
			"url":"http://www.poker.com"
			}""")

		then:
			response.status == 200
	}

	def 'Bob checks he has anonymous messages and finds a conflict for Richard'(){
		given:

		when:
			def response = appService.getAnonymousMessages(bobDunnURL, bobDunnPassword)

		then:
			response.status == 200
			response.responseData._embedded.goalConflictMessages.size() == 1
			response.responseData._embedded.goalConflictMessages[0].nickname == "RQ"
			response.responseData._embedded.goalConflictMessages[0].goalName == "gambling"
			response.responseData._embedded.goalConflictMessages[0].url =~ /poker/
	}

	def 'Richard checks he has anonymous messages and finds a conflict for himself'(){
		given:

		when:
			def response = appService.getAnonymousMessages(richardQuinURL, richardQuinPassword)

		then:
			response.status == 200
			response.responseData._embedded.goalConflictMessages.size() == 1
			response.responseData._embedded.goalConflictMessages[0].nickname == "<self>"
			response.responseData._embedded.goalConflictMessages[0].goalName == "gambling"
			response.responseData._embedded.goalConflictMessages[0].url =~ /poker/
	}
}
