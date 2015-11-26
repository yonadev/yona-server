package nu.yona.server

import groovy.json.*
import spock.lang.Shared
import spock.lang.Specification

class DeleteDirectMessageTest extends Specification {

	def adminServiceBaseURL = System.properties.'yona.adminservice.url'
	def YonaServer adminService = new YonaServer(adminServiceBaseURL)

	def analysisServiceBaseURL = System.properties.'yona.analysisservice.url'
	def YonaServer analysisService = new YonaServer(analysisServiceBaseURL)

	def appServiceBaseURL = System.properties.'yona.appservice.url'
	def YonaServer appService = new YonaServer(appServiceBaseURL)
	@Shared
	def timestamp = YonaServer.getTimeStamp()

	@Shared
	def richardQuinPassword = "R i c h a r d"
	def bobDunnPassword = "B o b"
	@Shared
	def richardQuinURL
	@Shared
	def richardQuinVPNLoginID
	@Shared
	def bobDunnURL
	@Shared
	def bobDunnVPNLoginID
	@Shared
	def richardQuinBobBuddyURL
	@Shared
	def bobDunnRichardBuddyURL
	@Shared
	def bobDunnBuddyMessageAcceptURL
	@Shared
	def bobDunnBuddyConnectRequestDeleteURL
	@Shared
	def bobDunnBuddyMessageProcessURL
	@Shared
	def richardQuinBuddyMessageAcceptURL
	@Shared
	def richardQuinBuddyMessageProcessURL
	@Shared
	def richardQuinGoalConflictMessageDeleteUrl
	@Shared
	def richardQuinBuddyMessageDeleteURL

	def 'Add user Richard Quin'(){
		given:

		when:
			def response = appService.addUser("""{
					"firstName":"Richard ${timestamp}",
					"lastName":"Quin ${timestamp}",
					"nickName":"RQ ${timestamp}",
					"mobileNumber":"+${timestamp}1",
					"devices":[
						"Nexus 6"
					],
					"goals":[
						"news"
					]
				}""", richardQuinPassword)
			if (response.status == 201) {
				richardQuinURL = appService.stripQueryString(response.responseData._links.self.href)
				richardQuinVPNLoginID = response.responseData.vpnProfile.vpnLoginID;
			}

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
					"firstName":"Bob ${timestamp}",
					"lastName":"Dunn ${timestamp}",
					"nickName":"BD ${timestamp}",
					"mobileNumber":"+${timestamp}2",
					"devices":[
						"iPhone 6"
					],
					"goals":[
						"gambling"
					]
				}""", bobDunnPassword)
			if (response.status == 201) {
				bobDunnURL = appService.stripQueryString(response.responseData._links.self.href)
				bobDunnVPNLoginID = response.responseData.vpnProfile.vpnLoginID;
			}

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
							"firstName":"Bob ${timestamp}",
							"lastName":"Dun ${timestamp}",
							"emailAddress":"bob${timestamp}@dunn.net",
							"mobileNumber":"+${timestamp}2"
						}
					},
					"message":"Would you like to be my buddy?"
				}""", richardQuinPassword)
			richardQuinBobBuddyURL = response.responseData._links.self.href

		then:
			response.status == 201
			response.responseData._embedded.user.firstName == "Bob ${timestamp}"
			richardQuinBobBuddyURL.startsWith(richardQuinURL)

		cleanup:
			println "URL buddy Richard: " + richardQuinBobBuddyURL
	}

	def 'Bob checks his direct messages'(){
		given:

		when:
			def response = appService.getDirectMessages(bobDunnURL, bobDunnPassword)
			if (response.responseData._embedded && response.responseData._embedded.buddyConnectRequestMessages) {
				bobDunnBuddyMessageAcceptURL = response.responseData._embedded.buddyConnectRequestMessages[0]._links.accept.href
				bobDunnBuddyConnectRequestDeleteURL = response.responseData._embedded.buddyConnectRequestMessages[0]._links.self.href
			}

		then:
			response.status == 200
			response.responseData._links.self.href == bobDunnURL + appService.DIRECT_MESSAGE_PATH_FRAGMENT
			response.responseData._embedded.buddyConnectRequestMessages[0].user.firstName == "Richard ${timestamp}"
			response.responseData._embedded.buddyConnectRequestMessages[0]._links.self.href.startsWith(response.responseData._links.self.href)
			bobDunnBuddyMessageAcceptURL.startsWith(response.responseData._embedded.buddyConnectRequestMessages[0]._links.self.href)
			bobDunnBuddyConnectRequestDeleteURL != null
	}

	def 'Bob tries to delete Richard\'s buddy request before it is processed'(){
		given:

		when:
			def response = appService.deleteResourceWithPassword(bobDunnBuddyConnectRequestDeleteURL, bobDunnPassword)

		then:
			response.status == 400
			response.responseData?.code == "error.cannot.delete.unprocessed.message"
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
			if (response.responseData._embedded && response.responseData._embedded.buddyConnectResponseMessages) {
				richardQuinBuddyMessageProcessURL = response.responseData._embedded.buddyConnectResponseMessages[0]._links.process.href
				richardQuinBuddyMessageDeleteURL = response.responseData._embedded.buddyConnectResponseMessages[0]._links.self.href
			}

		then:
			response.status == 200
			response.responseData._links.self.href == richardQuinURL + appService.DIRECT_MESSAGE_PATH_FRAGMENT
			response.responseData._embedded.buddyConnectResponseMessages[0].user.firstName == "Bob ${timestamp}"
			response.responseData._embedded.buddyConnectResponseMessages[0]._links.self.href.startsWith(response.responseData._links.self.href)
			richardQuinBuddyMessageProcessURL.startsWith(response.responseData._embedded.buddyConnectResponseMessages[0]._links.self.href)
			richardQuinBuddyMessageDeleteURL != null
	}

	def 'Richard tries to delete Bob\'s buddy acceptance before it is processed'(){
		given:

		when:
			def response = appService.deleteResourceWithPassword(richardQuinBuddyMessageDeleteURL, richardQuinPassword)

		then:
			response.status == 400
			response.responseData?.code == "error.cannot.delete.unprocessed.message"
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

	def 'Richard checks he has no anonymous messages'(){
		given:

		when:
			def response = appService.getAnonymousMessages(richardQuinURL, richardQuinPassword)

		then:
			response.status == 200
			response.responseData._embedded == null || response.responseData._embedded.goalConflictMessages == null
	}

	def 'Bob checks he has no anonymous messages'(){
		given:

		when:
			def response = appService.getAnonymousMessages(bobDunnURL, bobDunnPassword)

		then:
			response.status == 200
			response.responseData._embedded == null || response.responseData._embedded.goalConflictMessages == null
	}

	def 'Classification engine detects a potential conflict for Richard'(){
		given:

		when:
			def response = analysisService.postToAnalysisEngine("""{
				"vpnLoginID":"${richardQuinVPNLoginID}",
				"categories": ["news/media"],
				"url":"http://www.refdag.nl"
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
			response.responseData._embedded.goalConflictMessages[0].nickname == "RQ ${timestamp}"
			response.responseData._embedded.goalConflictMessages[0].goalName == "news"
			response.responseData._embedded.goalConflictMessages[0].url =~ /refdag/
	}

	def 'Richard checks he has anonymous messages and finds a conflict for himself'(){
		given:

		when:
			def response = appService.getAnonymousMessages(richardQuinURL, richardQuinPassword)
			richardQuinGoalConflictMessageDeleteUrl = response.responseData?._embedded?.goalConflictMessages[0]._links?.self?.href

		then:
			response.status == 200
			response.responseData._embedded.goalConflictMessages.size() == 1
			response.responseData._embedded.goalConflictMessages[0].nickname == "<self>"
			response.responseData._embedded.goalConflictMessages[0].goalName == "news"
			response.responseData._embedded.goalConflictMessages[0].url =~ /refdag/
			richardQuinGoalConflictMessageDeleteUrl != null
	}

	def 'Richard deletes his own goal conflict message'(){
		given:

		when:
			def response = appService.deleteResourceWithPassword(richardQuinGoalConflictMessageDeleteUrl, richardQuinPassword)

		then:
			response.status == 200
	}

	def 'Richard checks he no longer has any anonymous messages'(){
		given:

		when:
			def response = appService.getAnonymousMessages(richardQuinURL, richardQuinPassword)

		then:
			response.status == 200
			response.responseData._embedded == null || response.responseData._embedded.goalConflictMessages == null
	}

	def 'Bob checks he still has a goal conflict message for Richard'(){
		given:

		when:
			def response = appService.getAnonymousMessages(bobDunnURL, bobDunnPassword)

		then:
			response.status == 200
			response.responseData._embedded.goalConflictMessages.size() == 1
			response.responseData._embedded.goalConflictMessages[0].nickname == "RQ ${timestamp}"
			response.responseData._embedded.goalConflictMessages[0].goalName == "news"
			response.responseData._embedded.goalConflictMessages[0].url =~ /refdag/
	}

	def 'Bob deletes Richard\'s buddy request'(){
		given:

		when:
			def response = appService.deleteResourceWithPassword(bobDunnBuddyConnectRequestDeleteURL, bobDunnPassword)

		then:
			response.status == 200
	}

	def 'Bob checks his direct messages to see that the buddy request message was deleted'(){
		given:

		when:
			def response = appService.getDirectMessages(bobDunnURL, bobDunnPassword)

		then:
			response.status == 200
			response.responseData._embedded?.buddyConnectRequestMessages?.length ?: 0 == 0
	}

	def 'Richard deletes Bob\'s buddy acceptance message'(){
		given:

		when:
			def response = appService.deleteResourceWithPassword(richardQuinBuddyMessageDeleteURL, richardQuinPassword)

		then:
			response.status == 200
	}

	def 'Richard checks his direct messages to see that the buddy acceptance message was deleted'(){
		given:

		when:
			def response = appService.getDirectMessages(richardQuinURL, richardQuinPassword)

		then:
			response.status == 200
			response.responseData._embedded?.buddyConnectRequestMessages?.length ?: 0 == 0
	}
}
