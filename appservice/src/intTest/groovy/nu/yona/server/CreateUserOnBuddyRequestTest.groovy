/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import static nu.yona.server.test.CommonAssertions.*

import groovy.json.*
import nu.yona.server.test.CommonAssertions
import nu.yona.server.test.Device
import nu.yona.server.test.User

class CreateUserOnBuddyRequestTest extends AbstractAppServiceIntegrationTest
{
	final def dummyTempPassword = "abcd"

	def 'Richard cannot create a buddy request before confirming his own mobile number'()
	{
		given:
		def richard = appService.addUser(CommonAssertions.&assertUserCreationResponseDetails, "Richard", "Quinn", "RQ",
				makeMobileNumber(timestamp))

		when:
		def response = sendBuddyRequestForBob(richard, makeMobileNumber(timestamp))

		then:
		assertResponseStatus(response, 400)
		response.responseData.code == "error.mobile.number.not.confirmed"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Richard successfully creates a buddy request after confirming his own mobile number'()
	{
		given:
		def richard = addRichard()

		when:
		def response = sendBuddyRequestForBob(richard, makeMobileNumber(timestamp))

		then:
		assertResponseStatus(response, 201)
		response.responseData._embedded."yona:user".firstName == "Bob"
		response.responseData._embedded."yona:user".appLastOpenedDate == null
		response.responseData._links."yona:user" == null
		response.responseData._links.self.href.startsWith(YonaServer.stripQueryString(richard.url))

		cleanup:
		appService.deleteUser(richard)
		// TODO: How to delete the invited user?
	}

	def 'Bob downloads the app and uses it to open the link sent in the e-mail and load the prefilled data provided by Richard'()
	{
		given:
		def richard = addRichard()
		def mobileNumberBob = makeMobileNumber(timestamp)
		def inviteUrl = buildInviteUrl(sendBuddyRequestForBob(richard, mobileNumberBob))

		when:
		def bob = appService.getUser(CommonAssertions.&assertUserGetResponseDetailsWithPrivateDataCreatedOnBuddyRequest, inviteUrl, true, null)

		then:
		bob.firstName == "Bob"
		bob.lastName == "Dunn"
		bob.mobileNumber == mobileNumberBob
		bob.mobileNumberConfirmationUrl != null

		cleanup:
		appService.deleteUser(richard)
		// Cannot delete Bob, as he did not set up a Yona password yet.
	}

	def 'Bob adjusts data and submits; app saves with the device password'()
	{
		given:
		def richard = addRichard()
		def mobileNumberBob = makeMobileNumber(timestamp)
		def inviteUrl = buildInviteUrl(sendBuddyRequestForBob(richard, mobileNumberBob))
		def bob = appService.getUser(CommonAssertions.&assertUserGetResponseDetailsWithPrivateDataCreatedOnBuddyRequest, inviteUrl, true, null)

		when:
		def newNickname = "Bobby"
		def updatedBobJson = bob.convertToJson()
		updatedBobJson.nickname = newNickname
		User updatedBob = appService.updateUserCreatedOnBuddyRequest(CommonAssertions.&assertUserUpdateResponseDetails, new User(updatedBobJson), inviteUrl)

		then:
		updatedBob.firstName == bob.firstName
		updatedBob.lastName == bob.lastName
		updatedBob.mobileNumber == bob.mobileNumber
		updatedBob.nickname == newNickname
		!(updatedBob.url ==~ /tempPassword/)

		def getUserResponse = appService.getUser(inviteUrl, true, null)
		assertResponseStatus(getUserResponse, 400)
		getUserResponse.responseData.code == "error.decrypting.data"

		User bobFromGetAfterUpdate = appService.reloadUser(updatedBob)
		bobFromGetAfterUpdate.firstName == bob.firstName
		bobFromGetAfterUpdate.lastName == bob.lastName
		bobFromGetAfterUpdate.mobileNumber == bob.mobileNumber
		bobFromGetAfterUpdate.nickname == newNickname
		bobFromGetAfterUpdate.goals == null // Mobile number not confirmed yet
		bobFromGetAfterUpdate.devices == null // Mobile number not confirmed yet
		bobFromGetAfterUpdate.url

		def getMessagesResponse = appService.yonaServer.getResourceWithPassword(YonaServer.stripQueryString(bobFromGetAfterUpdate.url) + "/messages/", bobFromGetAfterUpdate.password)
		assertResponseStatus(getMessagesResponse, 400)
		getMessagesResponse.responseData.code == "error.mobile.number.not.confirmed"

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(updatedBob)
	}

	def 'Bob adjusts data, including his Android device'()
	{
		given:
		def richard = addRichard()
		def mobileNumberBob = makeMobileNumber(timestamp)
		def inviteUrl = buildInviteUrl(sendBuddyRequestForBob(richard, mobileNumberBob))
		def bob = appService.getUser(CommonAssertions.&assertUserGetResponseDetailsWithPrivateDataCreatedOnBuddyRequest, inviteUrl, true, null)

		when:
		def updatedBobJson = bob.convertToJson()
		updatedBobJson.nickname = "Bobby"
		User bobToBeUpdated = new User(updatedBobJson)
		bobToBeUpdated.deviceName = "My S8"
		bobToBeUpdated.deviceOperatingSystem = "ANDROID"
		bobToBeUpdated.deviceAppVersion = Device.SUPPORTED_APP_VERSION
		User updatedBob = appService.updateUserCreatedOnBuddyRequest(CommonAssertions.&assertUserUpdateResponseDetails, bobToBeUpdated, inviteUrl)

		then:
		updatedBob.devices == null // Mobile number not confirmed yet

		def bobWithConfirmedNumber = appService.confirmMobileNumber({ assertResponseStatusSuccess(it)}, updatedBob)
		bobWithConfirmedNumber.devices.size() == 1
		bobWithConfirmedNumber.devices[0].name == "My S8"
		bobWithConfirmedNumber.devices[0].operatingSystem == "ANDROID"

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(updatedBob)
	}

	def 'Bob updates his user information saved with the device password'()
	{
		given:
		def richard = addRichard()
		def mobileNumberBob = makeMobileNumber(timestamp)
		def inviteUrl = buildInviteUrl(sendBuddyRequestForBob(richard, mobileNumberBob))
		def bob = appService.getUser(CommonAssertions.&assertUserGetResponseDetailsWithPrivateDataCreatedOnBuddyRequest, inviteUrl, true, null)
		def newNickname = "Bobby"
		def newPassword = "B o b"
		def updatedBobJson = bob.convertToJson()
		updatedBobJson.yonaPassword = newPassword
		updatedBobJson.nickname = newNickname
		User updatedBob = appService.updateUserCreatedOnBuddyRequest(CommonAssertions.&assertUserUpdateResponseDetails, new User(updatedBobJson), inviteUrl)
		def bobFromGetAfterUpdate = appService.getUser(CommonAssertions.&assertUserGetResponseDetailsWithPrivateData, bob.url, true, updatedBob.password)

		when:
		def againChangedNickname = "Robert"
		def againUpdatedBobJson = bobFromGetAfterUpdate.convertToJson()
		againUpdatedBobJson.nickname = againChangedNickname
		def againUpdatedBob = appService.updateUser(CommonAssertions.&assertUserUpdateResponseDetails, new User(againUpdatedBobJson))

		then:
		againUpdatedBob.nickname == againChangedNickname

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob.url, newPassword)
	}

	def 'Bob receives confirmation SMS and enters the confirmation code in app'()
	{
		given:
		def richard = addRichard()
		def mobileNumberBob = makeMobileNumber(timestamp)
		def inviteUrl = buildInviteUrl(sendBuddyRequestForBob(richard, mobileNumberBob))
		def bob = appService.getUser(CommonAssertions.&assertUserGetResponseDetailsWithPrivateDataCreatedOnBuddyRequest, inviteUrl, true, null)
		def newNickname = "Bobby"
		def newPassword = "B o b"
		def updatedBobJson = bob.convertToJson()
		updatedBobJson.yonaPassword = newPassword
		updatedBobJson.nickname = newNickname
		User updatedBob = appService.updateUserCreatedOnBuddyRequest(CommonAssertions.&assertUserUpdateResponseDetails, new User(updatedBobJson), inviteUrl)

		when:
		updatedBob = appService.confirmMobileNumber(CommonAssertions.&assertResponseStatusSuccess, updatedBob)

		then:
		def getUserResponse = appService.getUser(updatedBob.url, true, updatedBob.password)
		assertResponseStatusOk(getUserResponse)
		getUserResponse.responseData._embedded."yona:goals"._embedded."yona:goals"
		getUserResponse.responseData._embedded."yona:goals"._embedded."yona:goals".size() == 1 //mandatory goal
		def getMessagesResponse = appService.getMessages(updatedBob)
		assertResponseStatusOk(getMessagesResponse)
		getMessagesResponse.responseData._embedded."yona:messages".size() == 1 // The buddy request from Richard

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(updatedBob)
	}

	def 'Bob accepts Richard\'s buddy request'()
	{
		given:
		def richard = addRichard()
		def mobileNumberBob = makeMobileNumber(timestamp)
		def inviteUrl = buildInviteUrl(sendBuddyRequestForBob(richard, mobileNumberBob))
		def bob = appService.getUser(CommonAssertions.&assertUserGetResponseDetailsWithPrivateDataCreatedOnBuddyRequest, inviteUrl, true, null)
		def newNickname = "Bobby"
		def newPassword = "B o b"
		def updatedBobJson = bob.convertToJson()
		updatedBobJson.yonaPassword = newPassword
		updatedBobJson.nickname = newNickname
		User updatedBob = appService.updateUserCreatedOnBuddyRequest(CommonAssertions.&assertUserUpdateResponseDetails, new User(updatedBobJson), inviteUrl)
		updatedBob = appService.confirmMobileNumber(CommonAssertions.&assertResponseStatusSuccess, updatedBob)
		def acceptUrl = appService.fetchBuddyConnectRequestMessage(updatedBob).acceptUrl

		when:
		def response = appService.postMessageActionWithPassword(acceptUrl, ["message" : "Yes, great idea!"], updatedBob.password)

		then:
		assertResponseStatusOk(response)
		def bobWithBuddy = appService.reloadUser(updatedBob)
		bobWithBuddy.buddies != null
		bobWithBuddy.buddies.size() == 1
		bobWithBuddy.buddies[0].user.firstName == "Richard"
		bobWithBuddy.buddies[0].sendingStatus == "ACCEPTED"
		bobWithBuddy.buddies[0].receivingStatus == "ACCEPTED"

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(updatedBob)
	}

	def 'Richard finds Bob\'s buddy acceptance'()
	{
		given:
		def richard = addRichard()
		def mobileNumberBob = makeMobileNumber(timestamp)
		def inviteUrl = buildInviteUrl(sendBuddyRequestForBob(richard, mobileNumberBob))
		def bob = appService.getUser(CommonAssertions.&assertUserGetResponseDetailsWithPrivateDataCreatedOnBuddyRequest, inviteUrl, true, null)
		def newNickname = "Bobby"
		def newPassword = "B o b"
		def updatedBobJson = bob.convertToJson()
		updatedBobJson.yonaPassword = newPassword
		updatedBobJson.nickname = newNickname
		User updatedBob = appService.updateUserCreatedOnBuddyRequest(CommonAssertions.&assertUserUpdateResponseDetails, new User(updatedBobJson), inviteUrl)
		updatedBob = appService.confirmMobileNumber(CommonAssertions.&assertResponseStatusSuccess, updatedBob)
		def acceptUrl = appService.fetchBuddyConnectRequestMessage(updatedBob).acceptUrl
		appService.postMessageActionWithPassword(acceptUrl, ["message" : "Yes, great idea!"], updatedBob.password)

		when:
		def response = appService.getMessages(richard)

		then:
		assertResponseStatusOk(response)
		def buddyConnectResponseMessages = response.responseData._embedded."yona:messages".findAll
		{ it."@type" == "BuddyConnectResponseMessage" }
		buddyConnectResponseMessages[0]._links?."yona:user"?.href.startsWith(YonaServer.stripQueryString(bob.url))
		buddyConnectResponseMessages[0]._embedded?."yona:user" == null
		buddyConnectResponseMessages[0].nickname == newNickname
		assertEquals(buddyConnectResponseMessages[0].creationTime, YonaServer.now)
		buddyConnectResponseMessages[0].status == "ACCEPTED"
		buddyConnectResponseMessages[0]._links.self.href.startsWith(YonaServer.stripQueryString(richard.messagesUrl))
		buddyConnectResponseMessages[0]._links."yona:process" == null // Processing happens automatically these days

		def richardWithBuddy = appService.reloadUser(richard)
		richardWithBuddy.buddies != null
		richardWithBuddy.buddies.size() == 1
		richardWithBuddy.buddies[0].user.firstName == "Bob"
		assertEquals(richardWithBuddy.buddies[0].user.appLastOpenedDate, YonaServer.now.toLocalDate())
		richardWithBuddy.buddies[0].sendingStatus == "ACCEPTED"
		richardWithBuddy.buddies[0].receivingStatus == "ACCEPTED"

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(updatedBob)
	}

	def 'Goal conflict of Richard is reported to Richard and Bob'()
	{
		given:
		def richard = addRichard()
		def mobileNumberBob = makeMobileNumber(timestamp)
		def inviteUrl = buildInviteUrl(sendBuddyRequestForBob(richard, mobileNumberBob))
		def bob = appService.getUser(CommonAssertions.&assertUserGetResponseDetailsWithPrivateDataCreatedOnBuddyRequest, inviteUrl, true, null)
		def newNickname = "Bobby"
		def newPassword = "B o b"
		def updatedBobJson = bob.convertToJson()
		updatedBobJson.yonaPassword = newPassword
		updatedBobJson.nickname = newNickname
		User updatedBob = appService.updateUserCreatedOnBuddyRequest(CommonAssertions.&assertUserUpdateResponseDetails, new User(updatedBobJson), inviteUrl)
		updatedBob = appService.confirmMobileNumber(CommonAssertions.&assertResponseStatusSuccess, updatedBob)
		def acceptUrl = appService.fetchBuddyConnectRequestMessage(updatedBob).acceptUrl
		appService.postMessageActionWithPassword(acceptUrl, ["message" : "Yes, great idea!"], updatedBob.password)
		assert appService.fetchBuddyConnectResponseMessage(richard).processUrl == null // Processing happens automatically these days

		when:
		analysisService.postToAnalysisEngine(richard, ["news/media"], "http://www.refdag.nl")

		then:
		def getMessagesRichardResponse = appService.getMessages(richard)
		assertResponseStatusOk(getMessagesRichardResponse)
		def richardGoalConflictMessages = getMessagesRichardResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		richardGoalConflictMessages.size() == 1
		richardGoalConflictMessages[0].nickname == "RQ (me)"
		richardGoalConflictMessages[0]._links."yona:activityCategory".href == NEWS_ACT_CAT_URL
		richardGoalConflictMessages[0].url == "http://www.refdag.nl"

		def getMessagesBobResponse = appService.getMessages(updatedBob)
		assertResponseStatusOk(getMessagesBobResponse)
		def bobGoalConflictMessages = getMessagesBobResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		bobGoalConflictMessages.size() == 1
		bobGoalConflictMessages[0].nickname == richard.nickname
		bobGoalConflictMessages[0]._links."yona:activityCategory".href == NEWS_ACT_CAT_URL
		bobGoalConflictMessages[0].url == null

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(updatedBob)
	}

	def 'Attempt to add another user with the same mobile number'()
	{
		given:
		def richard = addRichard()
		def mobileNumberBob = makeMobileNumber(timestamp)
		sendBuddyRequestForBob(richard, mobileNumberBob)

		when:
		def john = appService.addUser(this.&assertUserCreationFailedBecauseOfDuplicate, "John", "Doe", "JD",
				mobileNumberBob)

		then:
		john == null

		cleanup:
		appService.deleteUser(richard)
		// TODO: How to delete the invited user?
	}

	def 'Hacking attempt: Try to get Bob with an invalid temp password'()
	{
		given:
		User richard = addRichard()
		def inviteUrl = buildInviteUrl(sendBuddyRequestForBob(richard, makeMobileNumber(timestamp)))

		when:
		def response = appService.getResource(YonaServer.stripQueryString(inviteUrl), [:], ["tempPassword": "hack", "requestingUserId": richard.getId()])

		then:
		assertResponseStatus(response, 400)
		response.responseData.code == "error.decrypting.data"

		cleanup:
		appService.deleteUser(richard)
		// TODO: How to delete the invited user?
	}

	def 'Hacking attempt: Try to update Bob Dunn with an invalid temp password'()
	{
		given:
		def richard = addRichard()
		def inviteUrl = buildInviteUrl(sendBuddyRequestForBob(richard, makeMobileNumber(timestamp)))

		when:
		def response = appService.updateResource(YonaServer.stripQueryString(inviteUrl), """{
				"firstName":"Richard",
				"lastName":"Quin",
				"nickname":"RQ",
				"mobileNumber":"${makeMobileNumber(timestamp)}"
			}""", [:], ["tempPassword": "hack"])

		then:
		assertResponseStatus(response, 400)
		response.responseData.code == "error.decrypting.data"

		cleanup:
		appService.deleteUser(richard)
		// TODO: How to delete the invited user?
	}

	def 'Hacking attempt: Try to get a normal user with a temp password'()
	{
		given:
		User richard = addRichard()

		when:
		def response = appService.getResource(richard.url, [:], ["tempPassword": richard.password, "requestingUserId": richard.getId()])

		then:
		assertResponseStatus(response, 400)
		response.responseData.code == "error.user.not.created.on.buddy.request"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Hacking attempt: Try to get a user created on buddy request with normal Yona password header'()
	{
		given:
		def richard = addRichard()
		def mobileNumberBob = makeMobileNumber(timestamp)
		def responseAddBuddy = sendBuddyRequestForBob(richard, mobileNumberBob)
		def bobUrl = responseAddBuddy.responseData._embedded."yona:user"._links.self.href
		def urlToTry = bobUrl.replaceAll(richard.getId(), User.getIdFromUrl(bobUrl)) // Correct requestingUserId

		when:
		def response = appService.getUser(urlToTry, true, dummyTempPassword)

		then:
		assertResponseStatus(response, 400)
		response.responseData.code == "error.user.created.on.buddy.request"

		cleanup:
		appService.deleteUser(richard)
		// Cannot delete Bob, as he did not set up a Yona password yet.
	}

	def 'Hacking attempt: Try to update a normal user with a temp password'()
	{
		given:
		def richard = addRichard()

		when:
		def response = appService.updateResource(YonaServer.stripQueryString(richard.url), """{
				"firstName":"Richard",
				"lastName":"Quin",
				"nickname":"RQ",
				"mobileNumber":"${makeMobileNumber(timestamp)}"
			}""", [:], ["tempPassword": "hack"])

		then:
		assertResponseStatus(response, 400)
		response.responseData.code == "error.user.not.created.on.buddy.request"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Overwrite user created on buddy request'()
	{
		given:
		def richard = addRichard()
		def mobileNumberBob = makeMobileNumber(timestamp)
		def inviteUrl = buildInviteUrl(sendBuddyRequestForBob(richard, mobileNumberBob))

		when:
		def response = appService.requestOverwriteUser(mobileNumberBob)

		then:
		assertResponseStatusOk(response)

		User bob = appService.addUser(this.&assertUserOverwriteResponseDetails, "Bob Changed",
				"Dunn Changed", "BD Changed", mobileNumberBob, ["overwriteUserConfirmationCode": "1234"])
		bob
		bob.firstName == "Bob Changed"
		bob.lastName == "Dunn Changed"
		bob.nickname == "BD Changed"
		bob.mobileNumber == mobileNumberBob
		bob.goals.size() == 1 //mandatory goal
		bob.goals[0].activityCategoryUrl == GAMBLING_ACT_CAT_URL

		def buddiesRichard = appService.getBuddies(richard)
		buddiesRichard.size() == 0

		def buddiesBob = appService.getBuddies(bob)
		buddiesBob.size() == 0

		def getMessagesResponse = appService.getMessages(richard)
		assertResponseStatusOk(getMessagesResponse)
		getMessagesResponse.responseData._embedded
		getMessagesResponse.responseData._embedded."yona:messages".size() == 1

		def buddyConnectResponseMessages = getMessagesResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "BuddyConnectResponseMessage"}
		def buddyConnectResponseMessage = buddyConnectResponseMessages[0]
		buddyConnectResponseMessage.message == "User account was deleted"
		buddyConnectResponseMessage.nickname == "Bob Dunn"
		buddyConnectResponseMessage._links.self.href.startsWith(YonaServer.stripQueryString(richard.messagesUrl))
		buddyConnectResponseMessage._links."yona:process" == null // Processing happens automatically these days

		User richardAfterBobOverwrite = appService.reloadUser(richard)
		richardAfterBobOverwrite.buddies.size() == 0

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Overwrite user created on buddy request and connect again'()
	{
		given:
		def richard = addRichard()
		def mobileNumberBob = makeMobileNumber(timestamp)
		def inviteUrl = buildInviteUrl(sendBuddyRequestForBob(richard, mobileNumberBob))
		appService.requestOverwriteUser(mobileNumberBob)
		User bob = appService.addUser(this.&assertUserOverwriteResponseDetails, "Bob Changed",
				"Dunn Changed", "BD Changed", mobileNumberBob, ["overwriteUserConfirmationCode": "1234"])
		bob.emailAddress = "bob@dunn.net"

		when:
		appService.makeBuddies(richard, bob)
		analysisService.postToAnalysisEngine(richard, ["news/media"], "http://www.refdag.nl")

		then:

		def buddiesRichard = appService.getBuddies(richard)
		buddiesRichard.size() == 1
		buddiesRichard[0].user.firstName == "Bob Changed"

		def buddiesBob = appService.getBuddies(bob)
		buddiesBob.size() == 1
		buddiesBob[0].user.firstName == "Richard"

		def getMessagesRichardResponse = appService.getMessages(richard)
		assertResponseStatusOk(getMessagesRichardResponse)
		def richardGoalConflictMessages = getMessagesRichardResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		richardGoalConflictMessages.size() == 1
		richardGoalConflictMessages[0].nickname == "RQ (me)"
		richardGoalConflictMessages[0]._links."yona:activityCategory".href == NEWS_ACT_CAT_URL
		richardGoalConflictMessages[0].url == "http://www.refdag.nl"

		def getMessagesBobResponse = appService.getMessages(bob)
		assertResponseStatusOk(getMessagesBobResponse)
		def bobGoalConflictMessages = getMessagesBobResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		bobGoalConflictMessages.size() == 1
		bobGoalConflictMessages[0].nickname == richard.nickname
		bobGoalConflictMessages[0]._links."yona:activityCategory".href == NEWS_ACT_CAT_URL
		bobGoalConflictMessages[0].url == null

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def assertUserCreationFailedBecauseOfDuplicate(response)
	{
		assertResponseStatus(response, 400)
		response.responseData.code == "error.user.exists.created.on.buddy.request"
	}

	def assertUserOverwriteResponseDetails(def response)
	{
		assertResponseStatusCreated(response)
		assertUserWithPrivateData(response.responseData)
	}

	def sendBuddyRequestForBob(User user, String mobileNumber)
	{
		appService.yonaServer.createResourceWithPassword(YonaServer.stripQueryString(user.url) + "/buddies/", """{
			"_embedded":{
				"yona:user":{
					"firstName":"Bob",
					"lastName":"Dunn",
					"emailAddress":"bobdunn325@gmail.com",
					"mobileNumber":"$mobileNumber"
				}
			},
			"message":"Would you like to be my buddy?",
			"sendingStatus":"REQUESTED",
			"receivingStatus":"REQUESTED"
		}""", user.password)
	}

	String buildInviteUrl(def response)
	{
		YonaServer.stripQueryString(response.responseData._embedded."yona:user"._links.self.href) + "?tempPassword=" + dummyTempPassword
	}
}
