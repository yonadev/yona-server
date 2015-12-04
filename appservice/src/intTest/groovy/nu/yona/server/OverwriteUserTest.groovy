package nu.yona.server

import groovy.json.*
import nu.yona.server.test.AbstractYonaIntegrationTest
import spock.lang.Shared

class OverwriteUserTest extends AbstractYonaIntegrationTest {

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
	def bobDunnMobileNumberConfirmationCode
	@Shared
	def richardQuinBobBuddyURL
	@Shared
	def bobDunnRichardBuddyURL
	@Shared
	def bobDunnBuddyMessageAcceptURL
	@Shared
	def richardQuinBuddyMessageProcessURL
	@Shared
	def richardQuinMobileNumberConfirmationCode

	@Shared
	def richardQuinNewURL
	@Shared
	def richardQuinNewPassword = "R i c h a r d 1"
	@Shared
	def richardQuinOverwriteConfirmationCode

	@Shared
	def userCreationMobileNumber = "+${timestamp}"
	@Shared
	def userCreationJSON = """{
				"firstName":"John",
				"lastName":"Doe ${timestamp}",
				"nickname":"JD ${timestamp}",
				"mobileNumber":"${userCreationMobileNumber}",
				"devices":[
					"Galaxy mini"
				],
				"goals":[
					"gambling"
				]}"""


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
		richardQuinMobileNumberConfirmationCode != null

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
		bobDunnMobileNumberConfirmationCode != null

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
		response.responseData._embedded.buddyConnectRequestMessages[0].user.firstName == "Richard ${timestamp}"
		response.responseData._embedded.buddyConnectRequestMessages[0].nickname == "RQ ${timestamp}"
		response.responseData._embedded.buddyConnectRequestMessages[0]._links.self.href.startsWith(bobDunnURL + appService.DIRECT_MESSAGES_PATH_FRAGMENT)
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

	def 'Attempt to add another user with the same mobile number'(){
		when:
		def response = appService.addUser("""{
					"firstName":"Richardo ${timestamp}",
					"lastName":"Quino ${timestamp}",
					"nickname":"RQo ${timestamp}",
					"mobileNumber":"+${timestamp}1",
					"devices":[
						"Nexus 6"
					],
					"goals":[
						"news"
					]
				}""", "Foo")

		then:
		response.status == 400
		response.responseData.code == "error.user.exists"
	}

	def 'Request overwrite of the existing user'(){
		when:
		def response = appService.requestOverwriteUser("+${timestamp}1")
		if(response.status == 200) {
			richardQuinOverwriteConfirmationCode = response.responseData.confirmationCode
		}
		then:
		response.status == 200
		richardQuinOverwriteConfirmationCode
	}

	def 'Overwrite the existing user'(){
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
				}""", richardQuinNewPassword, ["overwriteUserConfirmationCode": richardQuinOverwriteConfirmationCode])
		if(response.status == 201) {
			richardQuinNewURL = appService.stripQueryString(response.responseData._links.self.href)
		}

		then:
		response.status == 201
		response.responseData.mobileNumberConfirmed == true
	}

	def 'Bob checks his anonymous messages; the conflict for the overwritten user Richard does not cause an exception'(){
		given:

		when:
		def response = appService.getAnonymousMessages(bobDunnURL, bobDunnPassword)

		then:
		response.status == 200
		response.responseData._embedded.goalConflictMessages.size() == 1
		response.responseData._embedded.goalConflictMessages[0].nickname == "RQ ${timestamp}"
		response.responseData._embedded.goalConflictMessages[0].goalName == "news"
		response.responseData._embedded.goalConflictMessages[0].url == null
		response.responseData._embedded.goalConflictMessages[0]._links.self.href.startsWith(bobDunnURL + appService.ANONYMOUS_MESSAGES_PATH_FRAGMENT)
	}

	def 'Classification engine detects a potential conflict for Bob; this gives no exception because of the overwritten buddy user'(){
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

	def 'Bob checks his buddy list; the buddy for overwritten user Richard will not cause an exception'(){
		given:

		when:
		def response = appService.getBuddies(bobDunnURL, bobDunnPassword);

		then:
		response.status == 200
		response.responseData._embedded.buddies.size() == 1
		response.responseData._embedded.buddies[0]._embedded.user.firstName == null
		response.responseData._embedded.buddies[0].nickname == "RQ ${timestamp}"
		response.responseData._embedded.buddies[0].sendingStatus == "ACCEPTED"
		response.responseData._embedded.buddies[0].receivingStatus == "ACCEPTED"
	}

	def 'Hacking attempt: Brute force overwrite mobile number confirmation'(){
		given:
		def userAddResponse = appService.addUser(userCreationJSON, "Password");
		def overwriteRequestResponse = appService.requestOverwriteUser(userCreationMobileNumber)
		def userURL = appService.stripQueryString(userAddResponse.responseData._links.self.href);
		def confirmationCode = overwriteRequestResponse.responseData.confirmationCode;

		when:
		def response1TimeWrong = appService.addUser(userCreationJSON, "New password", ["overwriteUserConfirmationCode": "${confirmationCode}1"])
		appService.addUser(userCreationJSON, "New password", ["overwriteUserConfirmationCode": "${confirmationCode}2"])
		appService.addUser(userCreationJSON, "New password", ["overwriteUserConfirmationCode": "${confirmationCode}3"])
		appService.addUser(userCreationJSON, "New password", ["overwriteUserConfirmationCode": "${confirmationCode}4"])
		def response5TimesWrong = appService.addUser(userCreationJSON, "New password", ["overwriteUserConfirmationCode": "${confirmationCode}5"])
		def response6TimesWrong = appService.addUser(userCreationJSON, "New password", ["overwriteUserConfirmationCode": "${confirmationCode}6"])
		def response7thTimeRight = appService.addUser(userCreationJSON, "New password", ["overwriteUserConfirmationCode": "${confirmationCode}"])

		then:
		confirmationCode != null
		userAddResponse.status == 201
		userAddResponse.responseData.mobileNumberConfirmed == false
		response1TimeWrong.status == 400
		response1TimeWrong.responseData.code == "error.mobile.number.confirmation.code.mismatch"
		response5TimesWrong.status == 400
		response5TimesWrong.responseData.code == "error.mobile.number.confirmation.code.mismatch"
		response6TimesWrong.status == 400
		response6TimesWrong.responseData.code == "error.too.many.wrong.attempts"
		response7thTimeRight.status == 400
		response7thTimeRight.responseData.code == "error.too.many.wrong.attempts"

		cleanup:
		if (userURL) {
			appService.deleteUser(userURL, "Password")
		}
	}

	def 'Delete users'(){
		when:
		def response1 = appService.deleteUser(richardQuinNewURL, richardQuinNewPassword)
		def response2 = appService.deleteUser(bobDunnURL, bobDunnPassword)

		then:
		response1.status == 200
		response2.status == 200
	}
}
