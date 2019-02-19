/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import static nu.yona.server.test.CommonAssertions.*

import groovy.json.*
import nu.yona.server.test.User

class OverwriteUserTest extends AbstractAppServiceIntegrationTest
{
	def 'Attempt to add another user with the same mobile number'()
	{
		given:
		def richard = addRichard()

		when:
		def duplicateUser = appService.addUser(this.&userExistsAsserter, "The", "Next", "TN",
				"$richard.mobileNumber")

		then:
		duplicateUser == null

		cleanup:
		appService.deleteUser(richard)
	}

	def userExistsAsserter(def response)
	{
		assertResponseStatus(response, 400)
		assert response.responseData.code == "error.user.exists"
	}

	def 'Richard gets a confirmation code when requesting to overwrite his account'()
	{
		given:
		def richard = addRichard()

		when:
		def response = appService.requestOverwriteUser(richard.mobileNumber)

		then:
		assertResponseStatusOk(response)

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Overwrite Richard while being a buddy of Bob'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		def richard = richardAndBob.richard
		def bob = richardAndBob.bob
		analysisService.postToAnalysisEngine(richard, ["news/media"], "http://www.refdag.nl")
		appService.requestOverwriteUser(richard.mobileNumber)

		when:
		User richardChanged = appService.addUser(this.&assertUserOverwriteResponseDetails, "${richard.firstName}Changed",
				"${richard.lastName}Changed", "${richard.nickname}Changed", richard.mobileNumber,
				["overwriteUserConfirmationCode": "1234"])

		then:
		richardChanged
		richardChanged.firstName == "${richard.firstName}Changed"
		richardChanged.lastName == "${richard.lastName}Changed"
		richardChanged.nickname == "${richard.nickname}Changed"
		richardChanged.mobileNumber == richard.mobileNumber
		richardChanged.goals.size() == 1 //mandatory goal
		richardChanged.goals[0].activityCategoryUrl == GAMBLING_ACT_CAT_URL

		def getMessagesResponse = appService.getMessages(bob)
		assertResponseStatusOk(getMessagesResponse)
		getMessagesResponse.responseData._embedded?."yona:messages"?.size() == 1
		def buddyDisconnectMessages = getMessagesResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "BuddyDisconnectMessage"}
		buddyDisconnectMessages.size() == 1
		def disconnectMessage = buddyDisconnectMessages[0]
		disconnectMessage.reason == "USER_ACCOUNT_DELETED"
		disconnectMessage.message == "User account was deleted"
		disconnectMessage.nickname == richard.nickname
		disconnectMessage._links.self.href.startsWith(YonaServer.stripQueryString(bob.messagesUrl))
		disconnectMessage._links."yona:process" == null // Processing happens automatically these days

		def buddies = appService.getBuddies(bob)
		buddies.size() == 0

		cleanup:
		appService.deleteUser(richardChanged)
		appService.deleteUser(bob)
	}

	def 'Overwrite Richard with pending buddy invitation to Bob'()
	{
		given:
		def richard = addRichard()
		def bob = addBob()
		bob.emailAddress = "bob@dunn.net"
		appService.sendBuddyConnectRequest(richard, bob)
		appService.requestOverwriteUser(richard.mobileNumber)

		when:
		User richardChanged = appService.addUser(this.&assertUserOverwriteResponseDetails, "${richard.firstName}Changed",
				"${richard.lastName}Changed", "${richard.nickname}Changed", richard.mobileNumber,
				["overwriteUserConfirmationCode": "1234"])

		then:
		richardChanged
		richardChanged.firstName == "${richard.firstName}Changed"

		def getMessagesBobResponse = appService.getMessages(bob)
		assertResponseStatusOk(getMessagesBobResponse)
		getMessagesBobResponse.responseData._embedded."yona:messages".size() == 1
		def buddyConnectRequestMessages = getMessagesBobResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "BuddyConnectRequestMessage"}
		buddyConnectRequestMessages.size() == 1
		buddyConnectRequestMessages[0].nickname == richard.nickname

		def acceptUrl = buddyConnectRequestMessages[0]._links?."yona:accept"?.href
		def acceptBuddyRequestResponse = appService.postMessageActionWithPassword(acceptUrl, ["message" : "Yes, great idea!"], bob.password)
		assertResponseStatus(acceptBuddyRequestResponse, 400)
		acceptBuddyRequestResponse.responseData.code == "error.user.not.found.id"

		def rejectUrl = buddyConnectRequestMessages[0]._links?."yona:reject"?.href
		def rejectBuddyRequestResponse = appService.postMessageActionWithPassword(rejectUrl, ["message" : "Too bad!"], bob.password)
		assertResponseStatusOk(rejectBuddyRequestResponse)

		def buddiesRichard = appService.getBuddies(richardChanged)
		buddiesRichard.size() == 0

		def buddiesBob = appService.getBuddies(bob)
		buddiesBob.size() == 0

		cleanup:
		appService.deleteUser(richardChanged)
		appService.deleteUser(bob)
	}

	def 'Overwrite Richard with pending buddy invitation from Bob'()
	{
		given:
		def richard = addRichard()
		def bob = addBob()
		richard.emailAddress = "richard@quinn.com"
		appService.sendBuddyConnectRequest(bob, richard)
		appService.requestOverwriteUser(richard.mobileNumber)

		when:
		User richardChanged = appService.addUser(this.&assertUserOverwriteResponseDetails, "${richard.firstName}Changed",
				"${richard.lastName}Changed", "${richard.nickname}Changed", richard.mobileNumber,
				["overwriteUserConfirmationCode": "1234"])

		then:
		richardChanged
		richardChanged.firstName == "${richard.firstName}Changed"

		def buddiesRichard = appService.getBuddies(richardChanged)
		buddiesRichard.size() == 0

		def buddiesBob = appService.getBuddies(bob)
		buddiesBob.size() == 0

		def getMessagesRichardResponse = appService.getMessages(richardChanged)
		assertResponseStatusOk(getMessagesRichardResponse)
		getMessagesRichardResponse.responseData._embedded == null

		def getMessagesBobResponse = appService.getMessages(bob)
		assertResponseStatusOk(getMessagesBobResponse)
		getMessagesBobResponse.responseData._embedded."yona:messages".size() == 1
		def buddyConnectResponseMessages = getMessagesBobResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "BuddyConnectResponseMessage"}
		buddyConnectResponseMessages.size() == 1
		def buddyConnectResponseMessage = buddyConnectResponseMessages[0]
		buddyConnectResponseMessage.message == "User account was deleted"
		buddyConnectResponseMessage.nickname == "$richard.firstName $richard.lastName"
		buddyConnectResponseMessage._links.self.href.startsWith(YonaServer.stripQueryString(bob.messagesUrl))
		buddyConnectResponseMessage._links?."yona:process" == null // Processing happens automatically these days

		cleanup:
		appService.deleteUser(richardChanged)
		appService.deleteUser(bob)
	}

	def 'Overwrite Richard while being a buddy of Bob and verify that Bob can move on with everything'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		def richard = richardAndBob.richard
		def bob = richardAndBob.bob
		appService.requestOverwriteUser(richard.mobileNumber)

		when:
		User richardChanged = appService.addUser(this.&assertUserOverwriteResponseDetails, "${richard.firstName}Changed",
				"${richard.lastName}Changed", "${richard.nickname}Changed", richard.mobileNumber,
				["overwriteUserConfirmationCode": "1234"])

		then:
		richardChanged
		richardChanged.firstName == "${richard.firstName}Changed"

		analysisService.postToAnalysisEngine(bob, ["Gambling"], "http://www.poker.com")
		analysisService.postToAnalysisEngine(richardChanged, "news/media", "http://www.refdag.nl")

		def getMessagesRichardResponse = appService.getMessages(richardChanged)
		assertResponseStatusOk(getMessagesRichardResponse)
		getMessagesRichardResponse.responseData._embedded == null

		def getMessagesBobResponse = appService.getMessages(bob)
		assertResponseStatusOk(getMessagesBobResponse)
		getMessagesBobResponse.responseData._embedded."yona:messages".size() == 2

		def goalConflictMessages = getMessagesBobResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		goalConflictMessages.size == 1
		goalConflictMessages[0].nickname == "BD (me)"
		goalConflictMessages[0]._links."yona:activityCategory".href == GAMBLING_ACT_CAT_URL
		goalConflictMessages[0].url =~ /poker/

		def buddyDisconnectMessages = getMessagesBobResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "BuddyDisconnectMessage"}
		buddyDisconnectMessages.size() == 1
		def disconnectMessage = buddyDisconnectMessages[0]
		disconnectMessage.reason == "USER_ACCOUNT_DELETED"
		disconnectMessage.message == "User account was deleted"
		disconnectMessage.nickname == richard.nickname
		disconnectMessage._links.self.href.startsWith(YonaServer.stripQueryString(bob.messagesUrl))
		disconnectMessage._links?."yona:process" == null // Processing happens automatically these days

		def buddies = appService.getBuddies(bob)
		buddies.size() == 0

		cleanup:
		appService.deleteUser(richardChanged)
		appService.deleteUser(bob)
	}

	def 'Overwrite Richard while being a buddy of Bob and verify Richard does not receive Bob\'s goal conflicts anymore'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob
		appService.requestOverwriteUser(richard.mobileNumber)
		User richardChanged = appService.addUser(this.&assertUserOverwriteResponseDetails, "${richard.firstName}Changed",
				"${richard.lastName}Changed", "${richard.nickname}Changed", richard.mobileNumber,
				["overwriteUserConfirmationCode": "1234"])

		when:
		def response = analysisService.postToAnalysisEngine(bob, ["Gambling"], "http://www.poker.com")

		def getMessagesResponse = appService.getMessages(richardChanged)
		assertResponseStatusOk(getMessagesResponse)
		getMessagesResponse.responseData._embedded == null

		then:
		assertResponseStatusNoContent(response)

		cleanup:
		appService.deleteUser(richardChanged)
		appService.deleteUser(bob)
	}

	def 'Overwrite Richard after requesting a confirmation code for the second time'()
	{
		given:
		def richard = addRichard()
		analysisService.postToAnalysisEngine(richard, ["news/media"], "http://www.refdag.nl")
		assertResponseStatusOk(appService.requestOverwriteUser(richard.mobileNumber))
		assertResponseStatusOk(appService.requestOverwriteUser(richard.mobileNumber))

		when:
		User richardChanged = appService.addUser(this.&assertUserOverwriteResponseDetails, "${richard.firstName}Changed",
				"${richard.lastName}Changed", "${richard.nickname}Changed", richard.mobileNumber,
				["overwriteUserConfirmationCode": "1234"])

		then:
		richardChanged
		richardChanged.firstName == "${richard.firstName}Changed"
		richardChanged.lastName == "${richard.lastName}Changed"
		richardChanged.nickname == "${richard.nickname}Changed"
		richardChanged.mobileNumber == richard.mobileNumber
		richardChanged.goals.size() == 1 //mandatory goal
		richardChanged.goals[0].activityCategoryUrl == GAMBLING_ACT_CAT_URL

		cleanup:
		appService.deleteUser(richardChanged)
	}

	def 'Hacking attempt: Brute force overwrite user overwrite confirmation'()
	{
		given:
		def userCreationMobileNumber = makeMobileNumber("${timestamp}99")
		def userCreationJson = """{
						"firstName":"John",
						"lastName":"Doe",
						"nickname":"JD",
						"mobileNumber":"${userCreationMobileNumber}"}"""

		def userAddResponse = appService.addUser(userCreationJson)
		def overwriteRequestResponse = appService.requestOverwriteUser(userCreationMobileNumber)
		def userUrl = YonaServer.stripQueryString(userAddResponse.responseData._links.self.href)

		when:
		def response1TimeWrong = appService.addUser(userCreationJson, ["overwriteUserConfirmationCode": "12341"])
		response1TimeWrong.responseData.remainingAttempts == 4
		appService.addUser(userCreationJson, ["overwriteUserConfirmationCode": "12342"])
		appService.addUser(userCreationJson, ["overwriteUserConfirmationCode": "12343"])
		def response4TimesWrong = appService.addUser(userCreationJson, ["overwriteUserConfirmationCode": "12344"])
		def response5TimesWrong = appService.addUser(userCreationJson, ["overwriteUserConfirmationCode": "12345"])
		def response6TimesWrong = appService.addUser(userCreationJson, ["overwriteUserConfirmationCode": "12346"])
		def response7thTimeRight = appService.addUser(userCreationJson, ["overwriteUserConfirmationCode": "1234"])

		then:
		assertResponseStatus(userAddResponse, 201)
		userAddResponse.responseData._links."yona:confirmMobileNumber".href != null
		assertResponseStatus(response1TimeWrong, 400)
		response1TimeWrong.responseData.code == "error.user.overwrite.confirmation.code.mismatch"
		response1TimeWrong.responseData.remainingAttempts == 4
		assertResponseStatus(response4TimesWrong, 400)
		response4TimesWrong.responseData.code == "error.user.overwrite.confirmation.code.mismatch"
		response4TimesWrong.responseData.remainingAttempts == 1
		assertResponseStatus(response5TimesWrong, 400)
		response5TimesWrong.responseData.code == "error.user.overwrite.confirmation.code.mismatch"
		response5TimesWrong.responseData.remainingAttempts == 0
		assertResponseStatus(response6TimesWrong, 400)
		response6TimesWrong.responseData.code == "error.user.overwrite.confirmation.code.too.many.failed.attempts"
		response6TimesWrong.responseData.remainingAttempts == null
		assertResponseStatus(response7thTimeRight, 400)
		response7thTimeRight.responseData.code == "error.user.overwrite.confirmation.code.too.many.failed.attempts"

		cleanup:
		if (userUrl)
		{
			appService.deleteUser(userUrl, "Password")
		}
	}

	private def assertUserOverwriteResponseDetails(def response)
	{
		assertResponseStatusCreated(response)
		assertUserWithPrivateData(response.responseData)
	}
}
