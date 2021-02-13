/*******************************************************************************
 * Copyright (c) 2015, 2021 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import static nu.yona.server.test.CommonAssertions.assertEquals
import static nu.yona.server.test.CommonAssertions.assertResponseStatus
import static nu.yona.server.test.CommonAssertions.assertResponseStatusCreated
import static nu.yona.server.test.CommonAssertions.assertResponseStatusNoContent
import static nu.yona.server.test.CommonAssertions.assertResponseStatusOk
import static nu.yona.server.test.CommonAssertions.assertUser

import nu.yona.server.test.User

class OverwriteUserTest extends AbstractAppServiceIntegrationTest
{
	def 'Attempt to add another user with the same mobile number'()
	{
		given:
		User richard = addRichard()

		when:
		def duplicateUser = appService.addUser(this.&userExistsAsserter, "The", "Next", "TN",
				"$richard.mobileNumber")

		then:
		duplicateUser == null

		cleanup:
		appService.deleteUser(richard)
	}

	private def userExistsAsserter(def response)
	{
		assertResponseStatus(response, 400)
		assert response.responseData.code == "error.user.exists"
	}

	def 'Richard gets a confirmation code when requesting to overwrite his account'()
	{
		given:
		User richard = addRichard()

		when:
		def response = appService.requestOverwriteUser(richard.mobileNumber)

		then:
		assertResponseStatusNoContent(response)

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Overwrite Richard while being a buddy of Bob'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob
		analysisService.postToAnalysisEngine(richard.requestingDevice, ["news/media"], "http://www.refdag.nl")
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
		def buddyDisconnectMessages = getMessagesResponse.responseData._embedded."yona:messages".findAll { it."@type" == "BuddyDisconnectMessage" }
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
		User richard = addRichard()
		User bob = addBob()
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
		def buddyConnectRequestMessages = getMessagesBobResponse.responseData._embedded."yona:messages".findAll { it."@type" == "BuddyConnectRequestMessage" }
		buddyConnectRequestMessages.size() == 1
		buddyConnectRequestMessages[0].nickname == richard.nickname

		def acceptUrl = buddyConnectRequestMessages[0]._links?."yona:accept"?.href
		def acceptBuddyRequestResponse = appService.postMessageActionWithPassword(acceptUrl, ["message": "Yes, great idea!"], bob.password)
		assertResponseStatus(acceptBuddyRequestResponse, 400)
		acceptBuddyRequestResponse.responseData.code == "error.user.not.found.id"

		def rejectUrl = buddyConnectRequestMessages[0]._links?."yona:reject"?.href
		def rejectBuddyRequestResponse = appService.postMessageActionWithPassword(rejectUrl, ["message": "Too bad!"], bob.password)
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
		User richard = addRichard()
		User bob = addBob()
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
		def buddyConnectResponseMessages = getMessagesBobResponse.responseData._embedded."yona:messages".findAll { it."@type" == "BuddyConnectResponseMessage" }
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

	def 'Overwrite Richard with pending buddy invitation from Bob and immediately send another buddy connect request'()
	{
		given:
		User richard = addRichard()
		User bob = addBob()
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

		when:
		def connectResp = appService.sendBuddyConnectRequest(richardChanged, makeUserForBuddyRequest(bob, "b@c.com"))

		then:
		assertResponseStatusCreated(connectResp)

		def buddiesBob = appService.getBuddies(bob)
		buddiesBob.size() == 0

		def getMessagesRichardResponse = appService.getMessages(richardChanged)
		assertResponseStatusOk(getMessagesRichardResponse)
		getMessagesRichardResponse.responseData._embedded == null

		def getMessagesBobResponse = appService.getMessages(bob)
		assertResponseStatusOk(getMessagesBobResponse)
		getMessagesBobResponse.responseData._embedded."yona:messages".size() == 2
		def buddyConnectResponseMessages = getMessagesBobResponse.responseData._embedded."yona:messages".findAll { it."@type" == "BuddyConnectResponseMessage" }
		buddyConnectResponseMessages.size() == 1
		def buddyConnectResponseMessage = buddyConnectResponseMessages[0]
		buddyConnectResponseMessage.message == "User account was deleted"
		buddyConnectResponseMessage.nickname == "$richard.firstName $richard.lastName"
		buddyConnectResponseMessage._links.self.href.startsWith(YonaServer.stripQueryString(bob.messagesUrl))
		buddyConnectResponseMessage._links?."yona:process" == null // Processing happens automatically these days

		def buddyConnectRequestMessages = getMessagesBobResponse.responseData._embedded."yona:messages".findAll { it."@type" == "BuddyConnectRequestMessage" }
		buddyConnectRequestMessages.size() == 1
		buddyConnectRequestMessages[0].nickname == richardChanged.nickname
		assertEquals(buddyConnectRequestMessages[0].creationTime, YonaServer.now)
		buddyConnectRequestMessages[0].status == "REQUESTED"
		buddyConnectRequestMessages[0]._embedded."yona:user".firstName == richardChanged.firstName
		buddyConnectRequestMessages[0]._links.keySet() == ["self", "yona:accept", "yona:reject", "yona:markRead"] as Set
		buddyConnectRequestMessages[0]._links.self.href.startsWith(YonaServer.stripQueryString(bob.messagesUrl))
		buddyConnectRequestMessages[0]._links.self.href.endsWith(buddyConnectRequestMessages[0].messageId.toString())
		buddyConnectRequestMessages[0]._links."yona:accept".href.startsWith(buddyConnectRequestMessages[0]._links.self.href)

		cleanup:
		appService.deleteUser(richardChanged)
		appService.deleteUser(bob)
	}

	def 'Overwrite Richard with an accepted but unprocessed buddy invitation from Bob'()
	{
		given:
		User richard = addRichard()
		User bob = addBob()
		richard.emailAddress = "richard@quinn.com"
		appService.sendBuddyConnectRequest(bob, richard)
		def acceptUrl = appService.fetchBuddyConnectRequestMessage(richard).acceptUrl
		def acceptResponse = appService.postMessageActionWithPassword(acceptUrl, ["message": "Yes, great idea!"], richard.password)
		assertResponseStatusOk(acceptResponse)
		analysisService.postToAnalysisEngine(richard.requestingDevice, ["Gambling"], "http://www.poker.com")
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
		def buddyConnectResponseMessages = getMessagesBobResponse.responseData._embedded."yona:messages".findAll { it."@type" == "BuddyConnectResponseMessage" }
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
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob
		appService.requestOverwriteUser(richard.mobileNumber)

		when:
		User richardChanged = appService.addUser(this.&assertUserOverwriteResponseDetails, "${richard.firstName}Changed",
				"${richard.lastName}Changed", "${richard.nickname}Changed", richard.mobileNumber,
				["overwriteUserConfirmationCode": "1234"])

		then:
		richardChanged
		richardChanged.firstName == "${richard.firstName}Changed"

		analysisService.postToAnalysisEngine(bob.requestingDevice, ["Gambling"], "http://www.poker.com")
		analysisService.postToAnalysisEngine(richardChanged.requestingDevice, "news/media", "http://www.refdag.nl")

		def getMessagesRichardResponse = appService.getMessages(richardChanged)
		assertResponseStatusOk(getMessagesRichardResponse)
		getMessagesRichardResponse.responseData._embedded == null

		def getMessagesBobResponse = appService.getMessages(bob)
		assertResponseStatusOk(getMessagesBobResponse)
		getMessagesBobResponse.responseData._embedded."yona:messages".size() == 2

		def goalConflictMessages = getMessagesBobResponse.responseData._embedded."yona:messages".findAll { it."@type" == "GoalConflictMessage" }
		goalConflictMessages.size == 1
		goalConflictMessages[0].nickname == "BD (me)"
		goalConflictMessages[0]._links."yona:activityCategory".href == GAMBLING_ACT_CAT_URL
		goalConflictMessages[0].url =~ /poker/

		def buddyDisconnectMessages = getMessagesBobResponse.responseData._embedded."yona:messages".findAll { it."@type" == "BuddyDisconnectMessage" }
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
		def response = analysisService.postToAnalysisEngine(bob.requestingDevice, ["Gambling"], "http://www.poker.com")

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
		User richard = addRichard()
		analysisService.postToAnalysisEngine(richard.requestingDevice, ["news/media"], "http://www.refdag.nl")
		assertResponseStatusNoContent(appService.requestOverwriteUser(richard.mobileNumber))
		assertResponseStatusNoContent(appService.requestOverwriteUser(richard.mobileNumber))

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

	def 'After Richard overwrote his account, Bob does not get goal conflict message of Richard anymore'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richardBeforeOverwrite = richardAndBob.richard
		User bob = richardAndBob.bob
		appService.requestOverwriteUser(richardBeforeOverwrite.mobileNumber)
		User richardAfterOverwrite = appService.addUser(this.&assertUserOverwriteResponseDetails, "${richardBeforeOverwrite.firstName}Changed",
				"${richardBeforeOverwrite.lastName}Changed", "${richardBeforeOverwrite.nickname}Changed", richardBeforeOverwrite.mobileNumber,
				["overwriteUserConfirmationCode": "1234"])
		bob = appService.reloadUser(bob)

		when:
		analysisService.postToAnalysisEngine(richardBeforeOverwrite.requestingDevice, ["news/media"], "http://www.refdag.nl")

		then:
		def responseGetMessagesBob = appService.getMessages(bob)
		assertResponseStatusOk(responseGetMessagesBob)
		assert responseGetMessagesBob.responseData._embedded?."yona:messages"?.find { it."@type" == "GoalConflictMessage" } == null

		cleanup:
		appService.deleteUser(richardAfterOverwrite)
		appService.deleteUser(bob)
	}

	private def assertUserOverwriteResponseDetails(def response)
	{
		assertResponseStatusCreated(response)
		assertUser(response.responseData)
	}

	def 'Concurrent requests cause no errors'()
	{
		given:
		User richard = addRichard()
		def numberOfTimes = 5

		when:
		def responses = appService.yonaServer.postJsonConcurrently(numberOfTimes, appService.OVERWRITE_USER_REQUEST_PATH, [:], ["mobileNumber": richard.mobileNumber])

		then:
		responses.size() == numberOfTimes
		def p = responses.each { assert it == 204 }

		cleanup:
		appService.deleteUser(richard)
	}
}
