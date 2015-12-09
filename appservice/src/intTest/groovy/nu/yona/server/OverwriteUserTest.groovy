package nu.yona.server

import groovy.json.*
import spock.lang.Shared

import nu.yona.server.YonaServer

class OverwriteUserTest extends AbstractAppServiceIntegrationTest
{
	def 'Attempt to add another user with the same mobile number'()
	{
		given:
			def richard = addRichard()

		when:
			def duplicateUser = newAppService.addUser(this.&userExistsAsserter, "A n o t h e r", "The", "Next", "TN",
				"$richard.mobileNumber", [ "Nexus 6" ], [ "news", "gambling" ])

		then:
			duplicateUser == null

		cleanup:
			newAppService.deleteUser(richard)
	}

	def userExistsAsserter(def response)
	{
		assert response.status == 400
		assert response.responseData.code == "error.user.exists"
	}

	def 'Richard gets a confirmation code when requesting to overwrite his account'()
	{
		given:
			def richard = addRichard()

		when:
			def response = newAppService.requestOverwriteUser(richard.mobileNumber)

		then:
			response.status == 200
			response.responseData.confirmationCode

		cleanup:
			newAppService.deleteUser(richard)
	}

	def 'Overwrite the existing user'()
	{
		given:
			def richardAndBob = addRichardAndBobAsBuddies()
			def richard = richardAndBob.richard
			def bob = richardAndBob.bob
			newAnalysisService.postToAnalysisEngine(richard, ["news/media"], "http://www.refdag.nl")
			def confirmationCode = newAppService.requestOverwriteUser(richard.mobileNumber)?.responseData?.confirmationCode

		when:
			def richardChanged = newAppService.addUser(newAppService.&assertUserOverwriteResponseDetails, "${richard.password}Changed", "${richard.firstName}Changed",
				"${richard.lastName}Changed", "${richard.nickname}Changed", richard.mobileNumber, ["Nokia"], ["news"],
				["overwriteUserConfirmationCode": confirmationCode])

		then:
			richardChanged
			richardChanged.firstName == "${richard.firstName}Changed"
			richardChanged.lastName == "${richard.lastName}Changed"
			richardChanged.nickname == "${richard.nickname}Changed"
			richardChanged.mobileNumber == richard.mobileNumber
			richardChanged.devices == ["Nokia"]
			richardChanged.goals == ["news"]

			def getAnonMessagesResponse = newAppService.getAnonymousMessages(bob.url, bob.password)
			getAnonMessagesResponse.status == 200
			getAnonMessagesResponse.responseData._embedded.goalConflictMessages.size() == 1
			getAnonMessagesResponse.responseData._embedded.goalConflictMessages[0].nickname == richard.nickname
			getAnonMessagesResponse.responseData._embedded.goalConflictMessages[0].goalName == "news"
			getAnonMessagesResponse.responseData._embedded.goalConflictMessages[0].url == null

			def buddies = newAppService.getBuddies(bob)
			buddies.size() == 1
			buddies[0].user == null
			buddies[0].nickname == richard.nickname // TODO: Shouldn't this change be communicated to Bob?
			buddies[0].sendingStatus == "ACCEPTED" // Shouldn't the status change now that the user is removed?
			buddies[0].receivingStatus == "ACCEPTED"

		cleanup:
			newAppService.deleteUser(richard)
			newAppService.deleteUser(bob)
	}

	def 'Classification engine detects a potential conflict for Bob after Richard overwrote his account'()
	{
		given:
			def richardAndBob = addRichardAndBobAsBuddies()
			def richard = richardAndBob.richard
			def bob = richardAndBob.bob
			def confirmationCode = newAppService.requestOverwriteUser(richard.mobileNumber)?.responseData?.confirmationCode
			def richardChanged = newAppService.addUser(newAppService.&assertUserOverwriteResponseDetails, "${richard.password}Changed", "${richard.firstName}Changed",
				"${richard.lastName}Changed", "${richard.nickname}Changed", richard.mobileNumber, ["Nokia"], ["news"],
				["overwriteUserConfirmationCode": confirmationCode])

		when:
			def response = newAnalysisService.postToAnalysisEngine(bob, ["Gambling"], "http://www.poker.com")

		then:
			response.status == 200

		cleanup:
			newAppService.deleteUser(richard)
			newAppService.deleteUser(bob)
	}

	def 'Bob removes overwritten user Richard as buddy'()
	{
		given:
			def richardAndBob = addRichardAndBobAsBuddies()
			def richard = richardAndBob.richard
			def bob = richardAndBob.bob
			def confirmationCode = newAppService.requestOverwriteUser(richard.mobileNumber)?.responseData?.confirmationCode
			def richardChanged = newAppService.addUser(newAppService.&assertUserOverwriteResponseDetails, "${richard.password}Changed", "${richard.firstName}Changed",
				"${richard.lastName}Changed", "${richard.nickname}Changed", richard.mobileNumber, ["Nokia"], ["news"],
				["overwriteUserConfirmationCode": confirmationCode])
			def buddy = newAppService.getBuddies(bob)[0]

		when:
			def response = newAppService.removeBuddy(bob, buddy, "Good bye friend")

		then:
			response.status == 200
			newAppService.getBuddies(bob).size() == 0

		cleanup:
			newAppService.deleteUser(richard)
			newAppService.deleteUser(bob)
	}

	def 'Hacking attempt: Brute force overwrite mobile number confirmation'()
	{
		given:
			def userCreationMobileNumber = "+${timestamp}99"
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

			def userAddResponse = newAppService.addUser(userCreationJSON, "Password")
			def overwriteRequestResponse = newAppService.requestOverwriteUser(userCreationMobileNumber)
			def userURL = YonaServer.stripQueryString(userAddResponse.responseData._links.self.href)
			def confirmationCode = overwriteRequestResponse.responseData.confirmationCode

		when:
			def response1TimeWrong = newAppService.addUser(userCreationJSON, "New password", ["overwriteUserConfirmationCode": "${confirmationCode}1"])
			newAppService.addUser(userCreationJSON, "New password", ["overwriteUserConfirmationCode": "${confirmationCode}2"])
			newAppService.addUser(userCreationJSON, "New password", ["overwriteUserConfirmationCode": "${confirmationCode}3"])
			newAppService.addUser(userCreationJSON, "New password", ["overwriteUserConfirmationCode": "${confirmationCode}4"])
			def response5TimesWrong = newAppService.addUser(userCreationJSON, "New password", ["overwriteUserConfirmationCode": "${confirmationCode}5"])
			def response6TimesWrong = newAppService.addUser(userCreationJSON, "New password", ["overwriteUserConfirmationCode": "${confirmationCode}6"])
			def response7thTimeRight = newAppService.addUser(userCreationJSON, "New password", ["overwriteUserConfirmationCode": "${confirmationCode}"])

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
				newAppService.deleteUser(userURL, "Password")
			}
	}
}
