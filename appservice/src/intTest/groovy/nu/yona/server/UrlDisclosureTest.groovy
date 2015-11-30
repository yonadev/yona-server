package nu.yona.server

import groovy.json.*
import spock.lang.Shared
import spock.lang.Specification

class UrlDisclosureTest extends Specification {

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
	def bobDunnBuddyMessageProcessURL
	@Shared
	def richardQuinBuddyMessageAcceptURL
	@Shared
	def richardQuinBuddyMessageProcessURL
	@Shared
	def bobDunnBuddyRemoveMessageProcessURL
	@Shared
	def disclosureRequest1URL
	@Shared
	def disclosureRequest2URL
	@Shared
	def goalDiscloseRequestMessage1AcceptURL
	@Shared
	def goalDiscloseRequestMessage2RejectURL

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
						"news",
						"gambling"
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
					"message":"Would you like to be my buddy?",
					"sendingStatus":"REQUESTED",
					"receivingStatus":"REQUESTED"
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
			}

		then:
			response.status == 200
			response.responseData._links.self.href == bobDunnURL + appService.DIRECT_MESSAGE_PATH_FRAGMENT
			response.responseData._embedded.buddyConnectRequestMessages[0].user.firstName == "Richard ${timestamp}"
			response.responseData._embedded.buddyConnectRequestMessages[0].nickname == "RQ ${timestamp}"
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
			if (response.responseData._embedded && response.responseData._embedded.buddyConnectResponseMessages) {
				richardQuinBuddyMessageProcessURL = response.responseData._embedded.buddyConnectResponseMessages[0]._links.process.href
			}

		then:
			response.status == 200
			response.responseData._links.self.href == richardQuinURL + appService.DIRECT_MESSAGE_PATH_FRAGMENT
			response.responseData._embedded.buddyConnectResponseMessages[0].user.firstName == "Bob ${timestamp}"
			response.responseData._embedded.buddyConnectResponseMessages[0].nickname == "BD ${timestamp}"
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
			response.responseData._embedded.buddies[0]._embedded.user.firstName == "Bob ${timestamp}"
			response.responseData._embedded.buddies[0].nickName == "BD ${timestamp}"
			response.responseData._embedded.buddies[0].sendingStatus == "ACCEPTED"
			response.responseData._embedded.buddies[0].receivingStatus == "ACCEPTED"
	}

	def 'Classification engine detects 2 potential conflicts for Richard'(){
		given:

		when:
			def response1 = analysisService.postToAnalysisEngine("""{
				"vpnLoginID":"${richardQuinVPNLoginID}",
				"categories": ["news/media"],
				"url":"http://www.refdag.nl"
				}""")
			def response2 = analysisService.postToAnalysisEngine("""{
				"vpnLoginID":"${richardQuinVPNLoginID}",
				"categories": ["Gambling"],
				"url":"http://www.poker.com"
				}""")

		then:
			response1.status == 200
			response2.status == 200
	}
	
	def 'Bob checks he has anonymous messages and finds 2 conflicts for Richard'(){
		given:

		when:
			def response = appService.getAnonymousMessages(bobDunnURL, bobDunnPassword)
			if(response.status == 200 && response.responseData._embedded.goalConflictMessages)
			{
				disclosureRequest1URL = response.responseData._embedded.goalConflictMessages[0]._links.requestDisclosure.href
				disclosureRequest2URL = response.responseData._embedded.goalConflictMessages[1]._links.requestDisclosure.href
			}

		then:
			response.status == 200
			response.responseData._embedded.goalConflictMessages.size() == 2
			response.responseData._embedded.goalConflictMessages[0].nickname == "RQ ${timestamp}"
			response.responseData._embedded.goalConflictMessages[0].goalName == "news"
			response.responseData._embedded.goalConflictMessages[0].url == null
			response.responseData._embedded.goalConflictMessages[0]._links.requestDisclosure
			response.responseData._embedded.goalConflictMessages[1].nickname == "RQ ${timestamp}"
			response.responseData._embedded.goalConflictMessages[1].goalName == "gambling"
			response.responseData._embedded.goalConflictMessages[1].url == null
			response.responseData._embedded.goalConflictMessages[1]._links.requestDisclosure
	}
	
	def 'Richard does not have disclosure request links on his own goal conflict messages'(){
		given:

		when:
			def response = appService.getAnonymousMessages(richardQuinURL, richardQuinPassword)

		then:
			response.status == 200
			response.responseData._embedded.goalConflictMessages.size() == 2
			response.responseData._embedded.goalConflictMessages[0].nickname == "<self>"
			response.responseData._embedded.goalConflictMessages[0].goalName == "news"
			response.responseData._embedded.goalConflictMessages[0].url == "http://www.refdag.nl"
			!response.responseData._embedded.goalConflictMessages[0]._links.requestDisclosure
			response.responseData._embedded.goalConflictMessages[1].nickname == "<self>"
			response.responseData._embedded.goalConflictMessages[1].goalName == "gambling"
			response.responseData._embedded.goalConflictMessages[1].url == "http://www.poker.com"
			!response.responseData._embedded.goalConflictMessages[1]._links.requestDisclosure
	}

	def 'Bob asks for disclosure of both'(){
		given:

		when:
			def response1 = appService.postMessageActionWithPassword(disclosureRequest1URL, """{
					"properties":{
					}
				}""", bobDunnPassword)
			def response2 = appService.postMessageActionWithPassword(disclosureRequest2URL, """{
					"properties":{
					}
				}""", bobDunnPassword)

		then:
			response1.status == 200
			response2.status == 200
	}

	def 'Richard checks his anonymous messages and finds the disclosure request'(){
		given:

		when:
			def response = appService.getAnonymousMessages(richardQuinURL, richardQuinPassword)
			if(response.status == 200 && response.responseData._embedded.discloseRequestMessages) {
				goalDiscloseRequestMessage1AcceptURL = response.responseData._embedded.discloseRequestMessages[0]._links.accept.href
				goalDiscloseRequestMessage2RejectURL = response.responseData._embedded.discloseRequestMessages[1]._links.reject.href
			}

		then:
			response.status == 200
			response.responseData._embedded.discloseRequestMessages
			response.responseData._embedded.discloseRequestMessages.size() == 2
			goalDiscloseRequestMessage1AcceptURL
			goalDiscloseRequestMessage2RejectURL
	}

	def 'Richard accepts the disclosure request of 1 and rejects of 2'(){
		given:

		when:
			def response1 = appService.postMessageActionWithPassword(goalDiscloseRequestMessage1AcceptURL, """{
					"properties":{
					}
				}""", richardQuinPassword)
			def response2 = appService.postMessageActionWithPassword(goalDiscloseRequestMessage2RejectURL, """{
					"properties":{
					}
				}""", richardQuinPassword)

		then:
			response1.status == 200
			response2.status == 200
	}

	def 'Bob checks he has anonymous messages and finds the URL of the first disclosed and the second denied'(){
		given:

		when:
			def response = appService.getAnonymousMessages(bobDunnURL, bobDunnPassword)

		then:
			response.status == 200
			response.responseData._embedded.goalConflictMessages.size() == 2
			response.responseData._embedded.goalConflictMessages[0].nickname == "RQ ${timestamp}"
			response.responseData._embedded.goalConflictMessages[0].goalName == "news"
			response.responseData._embedded.goalConflictMessages[0].url == "http://www.refdag.nl"
			response.responseData._embedded.goalConflictMessages[0].status == "DISCLOSE_ACCEPTED"
			!response.responseData._embedded.goalConflictMessages[0]._links.requestDisclosure
			response.responseData._embedded.goalConflictMessages[1].nickname == "RQ ${timestamp}"
			response.responseData._embedded.goalConflictMessages[1].goalName == "gambling"
			response.responseData._embedded.goalConflictMessages[1].url == null
			response.responseData._embedded.goalConflictMessages[1].status == "DISCLOSE_REJECTED"
			!response.responseData._embedded.goalConflictMessages[1]._links.requestDisclosure
	}
}
