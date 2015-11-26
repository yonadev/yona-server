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

		then:
		response.status == 201
		response.responseData._embedded.user.firstName == "Bob ${timestamp}"
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

	def 'Classification engine detects a potential conflict for Bob (second conflict message)'(){
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

	def 'Bob checks his message list'(){
		given:

		when:
		def response = appService.getAllMessages(bobDunnURL, bobDunnPassword)

		then:
		response.status == 200
		response.responseData._embedded.buddyConnectRequestMessages.size() == 1
		response.responseData._embedded.goalConflictMessages.size() == 2
	}

	def 'Delete users'(){
		given:

		when:
		def responseRichard = appService.deleteUser(richardQuinURL, richardQuinPassword)
		def responseBob = appService.deleteUser(bobDunnURL, bobDunnPassword)

		then:
		responseRichard.status == 200
		responseBob.status == 200
	}
}
