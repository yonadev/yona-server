package nu.yona.server

import groovy.json.*
import spock.lang.Shared
import spock.lang.Specification

class MessagingTest extends Specification {

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
	def bobDunnBuddyMessageAcceptURL
	@Shared
	def richardQuinBuddyMessageProcessURL

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
						"gambling", "news"
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

	def 'Richard requests Bob to become his buddy (generates a BuddyConnectRequestMessage)'(){
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

		then:
		response.status == 201
		response.responseData._embedded.user.firstName == "Bob ${timestamp}"
	}

	def 'Bob checks his direct message list'(){
		given:

		when:
		def response = appService.getDirectMessages(bobDunnURL, bobDunnPassword)
		if(response.responseData._embedded.buddyConnectRequestMessages) {
			bobDunnBuddyMessageAcceptURL = response.responseData._embedded.buddyConnectRequestMessages[0]._links.accept.href
		}

		then:
		response.status == 200
		response.responseData._links.self.href == bobDunnURL + appService.DIRECT_MESSAGES_PATH_FRAGMENT + "{?page,size,sort}"
		response.responseData._embedded.buddyConnectRequestMessages
		response.responseData._embedded.buddyConnectRequestMessages.size() == 1
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

	def 'Richard checks his anonymous messages'(){
		given:

		when:
		def response = appService.getAnonymousMessages(richardQuinURL, richardQuinPassword)
		if (response.responseData._embedded && response.responseData._embedded.buddyConnectResponseMessages) {
			richardQuinBuddyMessageProcessURL = response.responseData._embedded.buddyConnectResponseMessages[0]._links.process.href
		}

		then:
		response.status == 200
		response.responseData._embedded.buddyConnectResponseMessages[0].user.firstName == "Bob ${timestamp}"
		response.responseData._embedded.buddyConnectResponseMessages[0].nickname == "BD ${timestamp}"
		response.responseData._embedded.buddyConnectResponseMessages[0]._links.self.href.startsWith(richardQuinURL + appService.ANONYMOUS_MESSAGES_PATH_FRAGMENT)
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

	def 'Classification engine detects a potential conflict for Bob'(){
		given:

		when:
		def response = analysisService.postToAnalysisEngine("""{
				"vpnLoginID":"${bobDunnVPNLoginID}",
				"categories": ["Gambling"],
				"url":"http://www.poker.com"
				}""")

		then:
		response.status == 200
	}

	/*def 'Delete user Richard (generates a BuddyDisconnectMessage)'(){
	 given:
	 when:
	 def response = appService.deleteUser(richardQuinURL, richardQuinPassword)
	 then:
	 response.status == 200
	 }*/

	def 'Classification engine detects a potential conflict for Bob (second conflict message)'(){
		given:

		when:
		def response = analysisService.postToAnalysisEngine("""{
				"vpnLoginID":"${bobDunnVPNLoginID}",
				"categories": ["news/media"],
				"url":"http://www.refdag.nl"
				}""")

		then:
		response.status == 200
	}

	def 'Bob checks his anonymous message list'(){
		given:

		when:
		def response = appService.getAnonymousMessages(bobDunnURL, bobDunnPassword)

		then:
		response.status == 200
		response.responseData._links.self.href == bobDunnURL + appService.ANONYMOUS_MESSAGES_PATH_FRAGMENT + "{?page,size,sort}"
		//response.responseData._embedded.buddyDisconnectMessages
		//response.responseData._embedded.buddyDisconnectMessages.size() == 1
		response.responseData._embedded.goalConflictMessages
		response.responseData._embedded.goalConflictMessages.size() == 2
	}

	def 'Delete users'(){
		given:

		when:
		def response = appService.deleteUser(bobDunnURL, bobDunnPassword)

		then:
		response.status == 200
	}
}
