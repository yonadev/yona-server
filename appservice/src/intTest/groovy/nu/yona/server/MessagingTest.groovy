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
	@Shared
	def richardQuinMobileNumberConfirmationCode
	@Shared
	def bobDunnMobileNumberConfirmationCode

	def 'Add user Richard Quin'(){
		given:

		when:
		def response = appService.addUser("""{
					"firstName":"Richard ${timestamp}",
					"lastName":"Quin ${timestamp}",
					"nickname":"RQ ${timestamp}",
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
			richardQuinMobileNumberConfirmationCode = response.responseData.confirmationCode;
		}

		then:
		response.status == 201
		richardQuinURL.startsWith(appServiceBaseURL + appService.USERS_PATH)

		cleanup:
		println "URL Richard: " + richardQuinURL
	}

	def 'Confirm Richard\'s mobile number'(){
		when:
		def response = appService.confirmMobileNumber(richardQuinURL, """ { "code":"${richardQuinMobileNumberConfirmationCode}" } """, richardQuinPassword)

		then:
		response.status == 200
		response.responseData.mobileNumberConfirmed == true
	}

	def 'Add user Bob Dunn'(){
		given:

		when:
		def response = appService.addUser("""{
					"firstName":"Bob ${timestamp}",
					"lastName":"Dunn ${timestamp}",
					"nickname":"BD ${timestamp}",
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
			bobDunnMobileNumberConfirmationCode = response.responseData.confirmationCode;
		}

		then:
		response.status == 201
		bobDunnURL.startsWith(appServiceBaseURL + appService.USERS_PATH)

		cleanup:
		println "URL Bob: " + bobDunnURL
	}

	def 'Confirm Bob\'s mobile number'(){
		when:
		def response = appService.confirmMobileNumber(bobDunnURL, """ { "code":"${bobDunnMobileNumberConfirmationCode}" } """, bobDunnPassword)

		then:
		response.status == 200
		response.responseData.mobileNumberConfirmed == true
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

	def 'Richard checks his full anonymous message list'(){
		given:

		when:
		def response = appService.getAnonymousMessages(richardQuinURL, richardQuinPassword)

		then:
		response.status == 200
		response.responseData._links.self.href == richardQuinURL + appService.ANONYMOUS_MESSAGES_PATH_FRAGMENT + "{?page,size,sort}"
		response.responseData._embedded.buddyConnectResponseMessages
		response.responseData._embedded.buddyConnectResponseMessages.size() == 1
		response.responseData._embedded.goalConflictMessages
		response.responseData._embedded.goalConflictMessages.size() == 3
	}

	def 'Richard checks first page of 2 of his anonymous message list'(){
		given:

		when:
		def response = appService.getAnonymousMessages(richardQuinURL, richardQuinPassword, [
			"page": 0,
			"size": 2,
			"sort": "creationTime"
		])

		then:
		response.status == 200
		response.responseData._links.self.href == richardQuinURL + appService.ANONYMOUS_MESSAGES_PATH_FRAGMENT + "?page=0&size=2&sort=creationTime"
		!response.responseData._links.prev
		response.responseData._links.next
		!response.responseData._embedded.buddyConnectResponseMessages
		response.responseData._embedded.goalConflictMessages
		response.responseData._embedded.goalConflictMessages.size() == 2
		response.responseData.page.totalElements == 4
	}

	def 'Richard checks second page of 2 of his anonymous message list'(){
		given:

		when:
		def response = appService.getAnonymousMessages(richardQuinURL, richardQuinPassword, [
			"page": 1,
			"size": 2,
			"sort": "creationTime"
		])

		then:
		response.status == 200
		response.responseData._links.self.href == richardQuinURL + appService.ANONYMOUS_MESSAGES_PATH_FRAGMENT + "?page=1&size=2&sort=creationTime"
		response.responseData._links.prev
		!response.responseData._links.next
		response.responseData._embedded.buddyConnectResponseMessages
		response.responseData._embedded.buddyConnectResponseMessages.size() == 1
		response.responseData._embedded.goalConflictMessages
		response.responseData._embedded.goalConflictMessages.size() == 1
		response.responseData.page.totalElements == 4
	}

	def 'Delete users'(){
		given:

		when:
		def response1 = appService.deleteUser(bobDunnURL, bobDunnPassword)
		def response2 = appService.deleteUser(richardQuinURL, richardQuinPassword)

		then:
		response1.status == 200
		response2.status == 200
	}
}
