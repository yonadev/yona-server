package nu.yona.server

import groovy.json.*
import spock.lang.Shared

class OverwriteUserTest extends AbstractAppServiceIntegrationTest {

	@Shared
	def richardQuin
	@Shared
	def bobDunn

	@Shared
	def bobDunnRichardBuddyURL

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

	def 'Add Richard and Bob who are buddies'(){
		given:

		when:
		richardQuin = addUser("Richard", "Quin")
		bobDunn = addUser("Bob", "Dunn")
		makeBuddies(richardQuin, bobDunn)

		then:
		richardQuin
		bobDunn
	}

	def 'Classification engine detects a potential conflict for Richard'(){
		given:

		when:
		def response = analysisService.postToAnalysisEngine("""{
				"vpnLoginID":"${richardQuin.vpnLoginID}",
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
					"mobileNumber":"${richardQuin.mobileNumber}",
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
		def response = appService.requestOverwriteUser("${richardQuin.mobileNumber}")
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
					"mobileNumber":"${richardQuin.mobileNumber}",
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
		def response = appService.getAnonymousMessages(bobDunn.url, bobDunn.password)

		then:
		response.status == 200
		response.responseData._embedded.goalConflictMessages.size() == 1
		response.responseData._embedded.goalConflictMessages[0].nickname == richardQuin.nickname
		response.responseData._embedded.goalConflictMessages[0].goalName == "news"
		response.responseData._embedded.goalConflictMessages[0].url == null
		response.responseData._embedded.goalConflictMessages[0]._links.self.href.startsWith(bobDunn.url + appService.ANONYMOUS_MESSAGES_PATH_FRAGMENT)
	}

	def 'Classification engine detects a potential conflict for Bob; this gives no exception because of the overwritten buddy user'(){
		given:

		when:
		def response = analysisService.postToAnalysisEngine("""{
				"vpnLoginID":"${bobDunn.vpnLoginID}",
				"categories": ["Gambling"],
				"url":"http://www.poker.com"
				}""")

		then:
		response.status == 200
	}

	def 'Bob checks his buddy list; the buddy for overwritten user Richard will not cause an exception'(){
		given:

		when:
		def response = appService.getBuddies(bobDunn.url, bobDunn.password);
		if(response.status == 200 && response.responseData._embedded) {
			bobDunnRichardBuddyURL = response.responseData._embedded.buddies[0]._links.self.href
		}

		then:
		response.status == 200
		response.responseData._embedded.buddies.size() == 1
		response.responseData._embedded.buddies[0]._embedded.user.firstName == null
		response.responseData._embedded.buddies[0].nickname == richardQuin.nickname
		response.responseData._embedded.buddies[0].sendingStatus == "ACCEPTED"
		response.responseData._embedded.buddies[0].receivingStatus == "ACCEPTED"
		bobDunnRichardBuddyURL
	}

	def 'Bob removes overwritten user Richard as buddy'() {
		given:
		when:
		def response = appService.removeBuddy(bobDunnRichardBuddyURL, bobDunn.password, null)

		then:
		response.status == 200
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
		def response2 = appService.deleteUser(bobDunn.url, bobDunn.password)

		then:
		response1.status == 200
		response2.status == 200
	}
}
