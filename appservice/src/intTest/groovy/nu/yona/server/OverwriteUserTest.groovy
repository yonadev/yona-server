/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import groovy.json.*

class OverwriteUserTest extends AbstractAppServiceIntegrationTest
{
	def 'Attempt to add another user with the same mobile number'()
	{
		given:
		def richard = addRichard()

		when:
		def duplicateUser = appService.addUser(this.&userExistsAsserter, "A n o t h e r", "The", "Next", "TN",
				"$richard.mobileNumber", ["Nexus 6"], ["news", "gambling"])

		then:
		duplicateUser == null

		cleanup:
		appService.deleteUser(richard)
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
		def response = appService.requestOverwriteUser(richard.mobileNumber)

		then:
		response.status == 200
		response.responseData.confirmationCode

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Overwrite the existing user'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		def richard = richardAndBob.richard
		def bob = richardAndBob.bob
		analysisService.postToAnalysisEngine(richard, ["news/media"], "http://www.refdag.nl")
		def confirmationCode = appService.requestOverwriteUser(richard.mobileNumber)?.responseData?.confirmationCode

		when:
		def richardChanged = appService.addUser(appService.&assertUserOverwriteResponseDetails, "${richard.password}Changed", "${richard.firstName}Changed",
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

		def getAnonMessagesResponse = appService.getAnonymousMessages(bob.url, bob.password)
		getAnonMessagesResponse.status == 200
		getAnonMessagesResponse.responseData._embedded.goalConflictMessages.size() == 1
		getAnonMessagesResponse.responseData._embedded.goalConflictMessages[0].nickname == richard.nickname
		getAnonMessagesResponse.responseData._embedded.goalConflictMessages[0].goalName == "news"
		getAnonMessagesResponse.responseData._embedded.goalConflictMessages[0].url == null

		def buddies = appService.getBuddies(bob)
		buddies.size() == 1
		buddies[0].user == null
		buddies[0].nickname == richard.nickname // TODO: Shouldn't this change be communicated to Bob?
		buddies[0].sendingStatus == "ACCEPTED" // Shouldn't the status change now that the user is removed?
		buddies[0].receivingStatus == "ACCEPTED"

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Classification engine detects a potential conflict for Bob after Richard overwrote his account'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		def richard = richardAndBob.richard
		def bob = richardAndBob.bob
		def confirmationCode = appService.requestOverwriteUser(richard.mobileNumber)?.responseData?.confirmationCode
		def richardChanged = appService.addUser(appService.&assertUserOverwriteResponseDetails, "${richard.password}Changed", "${richard.firstName}Changed",
				"${richard.lastName}Changed", "${richard.nickname}Changed", richard.mobileNumber, ["Nokia"], ["news"],
				["overwriteUserConfirmationCode": confirmationCode])

		when:
		def response = analysisService.postToAnalysisEngine(bob, ["Gambling"], "http://www.poker.com")

		then:
		response.status == 200

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Bob removes overwritten user Richard as buddy'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		def richard = richardAndBob.richard
		def bob = richardAndBob.bob
		def confirmationCode = appService.requestOverwriteUser(richard.mobileNumber)?.responseData?.confirmationCode
		def richardChanged = appService.addUser(appService.&assertUserOverwriteResponseDetails, "${richard.password}Changed", "${richard.firstName}Changed",
				"${richard.lastName}Changed", "${richard.nickname}Changed", richard.mobileNumber, ["Nokia"], ["news"],
				["overwriteUserConfirmationCode": confirmationCode])
		def buddy = appService.getBuddies(bob)[0]

		when:
		def response = appService.removeBuddy(bob, buddy, "Good bye friend")

		then:
		response.status == 200
		appService.getBuddies(bob).size() == 0

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Hacking attempt: Brute force overwrite mobile number confirmation'()
	{
		given:
		def userCreationMobileNumber = "+${timestamp}99"
		def userCreationJSON = """{
						"firstName":"John",
						"lastName":"Doe",
						"nickname":"JD",
						"mobileNumber":"${userCreationMobileNumber}",
						"devices":[
							"Galaxy mini"
						],
						"goals":[
							"gambling"
						]}"""

		def userAddResponse = appService.addUser(userCreationJSON, "Password")
		def overwriteRequestResponse = appService.requestOverwriteUser(userCreationMobileNumber)
		def userURL = YonaServer.stripQueryString(userAddResponse.responseData._links.self.href)
		def confirmationCode = overwriteRequestResponse.responseData.confirmationCode

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
		userAddResponse.responseData._links?.confirmMobileNumber?.href != null
		response1TimeWrong.status == 400
		response1TimeWrong.responseData.code == "error.mobile.number.confirmation.code.mismatch"
		response5TimesWrong.status == 400
		response5TimesWrong.responseData.code == "error.mobile.number.confirmation.code.mismatch"
		response6TimesWrong.status == 400
		response6TimesWrong.responseData.code == "error.too.many.wrong.attempts"
		response7thTimeRight.status == 400
		response7thTimeRight.responseData.code == "error.too.many.wrong.attempts"

		cleanup:
		if (userURL)
		{
			appService.deleteUser(userURL, "Password")
		}
	}
}
