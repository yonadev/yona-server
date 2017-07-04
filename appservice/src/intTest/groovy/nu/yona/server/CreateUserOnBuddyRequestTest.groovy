/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import groovy.json.*
import nu.yona.server.test.User

class CreateUserOnBuddyRequestTest extends AbstractAppServiceIntegrationTest
{
	final def dummyTempPassword = "abcd"

	def 'Richard cannot create a buddy request before confirming his own mobile number'()
	{
		given:
		def richard = appService.addUser(appService.&assertUserCreationResponseDetails, "Richard", "Quinn", "RQ",
				"+$timestamp")

		when:
		def response = sendBuddyRequestForBob(richard, "+$timestamp")

		then:
		response.status == 400
		response.responseData.code == "error.mobile.number.not.confirmed"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Richard successfully creates a buddy request after confirming his own mobile number'()
	{
		given:
		def richard = addRichard()

		when:
		def response = sendBuddyRequestForBob(richard, "+$timestamp")

		then:
		response.status == 201
		response.responseData._embedded."yona:user".firstName == "Bob"
		response.responseData._links."yona:user" == null
		response.responseData._links.self.href.startsWith(richard.url)

		cleanup:
		appService.deleteUser(richard)
		// TODO: How to delete the invited user?
	}

	def 'Bob downloads the app and uses it to open the link sent in the e-mail and load the prefilled data provided by Richard'()
	{
		given:
		def richard = addRichard()
		def mobileNumberBob = "+$timestamp"
		def inviteUrl = buildInviteUrl(sendBuddyRequestForBob(richard, mobileNumberBob))

		when:
		def bob = appService.getUser(appService.&assertUserGetResponseDetailsWithPrivateDataCreatedOnBuddyRequest, inviteUrl, true, null)

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
		def mobileNumberBob = "+$timestamp"
		def inviteUrl = buildInviteUrl(sendBuddyRequestForBob(richard, mobileNumberBob))
		def bob = appService.getUser(appService.&assertUserGetResponseDetailsWithPrivateDataCreatedOnBuddyRequest, inviteUrl, true, null)

		when:
		def newNickname = "Bobby"
		def updatedBobJson = bob.convertToJson()
		updatedBobJson.nickname = newNickname
		User updatedBob = appService.updateUserCreatedOnBuddyRequest(appService.&assertUserUpdateResponseDetails, new User(updatedBobJson), inviteUrl)

		then:
		updatedBob.firstName == bob.firstName
		updatedBob.lastName == bob.lastName
		updatedBob.mobileNumber == bob.mobileNumber
		updatedBob.nickname == newNickname
		!(updatedBob.url ==~ /tempPassword/)

		def getUserResponse = appService.getUser(inviteUrl, true, null)
		getUserResponse.status == 400
		getUserResponse.responseData.code == "error.decrypting.data"

		User bobFromGetAfterUpdate = appService.reloadUser(updatedBob)
		bobFromGetAfterUpdate.firstName == bob.firstName
		bobFromGetAfterUpdate.lastName == bob.lastName
		bobFromGetAfterUpdate.mobileNumber == bob.mobileNumber
		bobFromGetAfterUpdate.nickname == newNickname
		bobFromGetAfterUpdate.goals.size() == 0 // Mobile number not confirmed yet
		bobFromGetAfterUpdate.url

		def getMessagesResponse = appService.yonaServer.getResourceWithPassword(bobFromGetAfterUpdate.url + "/messages/", bobFromGetAfterUpdate.password)
		getMessagesResponse.status == 400
		getMessagesResponse.responseData.code == "error.mobile.number.not.confirmed"

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(updatedBob)
	}

	def 'Bob updates his user information saved with the device password'()
	{
		given:
		def richard = addRichard()
		def mobileNumberBob = "+$timestamp"
		def inviteUrl = buildInviteUrl(sendBuddyRequestForBob(richard, mobileNumberBob))
		def bob = appService.getUser(appService.&assertUserGetResponseDetailsWithPrivateDataCreatedOnBuddyRequest, inviteUrl, true, null)
		def newNickname = "Bobby"
		def newPassword = "B o b"
		def updatedBobJson = bob.convertToJson()
		updatedBobJson.yonaPassword = newPassword
		updatedBobJson.nickname = newNickname
		User updatedBob = appService.updateUserCreatedOnBuddyRequest(appService.&assertUserUpdateResponseDetails, new User(updatedBobJson), inviteUrl)
		def bobFromGetAfterUpdate = appService.getUser(appService.&assertUserGetResponseDetailsWithPrivateDataCreatedOnBuddyRequest, bob.url, true, updatedBob.password)

		when:
		def againChangedNickname = "Robert"
		def againUpdatedBobJson = bobFromGetAfterUpdate.convertToJson()
		againUpdatedBobJson.nickname = againChangedNickname
		def againUpdatedBob = appService.updateUser(appService.&assertUserUpdateResponseDetails, new User(againUpdatedBobJson))

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
		def mobileNumberBob = "+$timestamp"
		def inviteUrl = buildInviteUrl(sendBuddyRequestForBob(richard, mobileNumberBob))
		def bob = appService.getUser(appService.&assertUserGetResponseDetailsWithPrivateDataCreatedOnBuddyRequest, inviteUrl, true, null)
		def newNickname = "Bobby"
		def newPassword = "B o b"
		def updatedBobJson = bob.convertToJson()
		updatedBobJson.yonaPassword = newPassword
		updatedBobJson.nickname = newNickname
		User updatedBob = appService.updateUserCreatedOnBuddyRequest(appService.&assertUserUpdateResponseDetails, new User(updatedBobJson), inviteUrl)

		when:
		updatedBob = appService.confirmMobileNumber(appService.&assertResponseStatusSuccess, updatedBob)

		then:
		def getUserResponse = appService.getUser(updatedBob.url, true, updatedBob.password)
		getUserResponse.status == 200
		getUserResponse.responseData._embedded."yona:goals"._embedded."yona:goals"
		getUserResponse.responseData._embedded."yona:goals"._embedded."yona:goals".size() == 1 //mandatory goal
		def getMessagesResponse = appService.getMessages(updatedBob)
		getMessagesResponse.status == 200
		getMessagesResponse.responseData._embedded."yona:messages".size() == 1 // The buddy request from Richard

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(updatedBob)
	}

	def 'Bob accepts Richard\'s buddy request'()
	{
		given:
		def richard = addRichard()
		def mobileNumberBob = "+$timestamp"
		def inviteUrl = buildInviteUrl(sendBuddyRequestForBob(richard, mobileNumberBob))
		def bob = appService.getUser(appService.&assertUserGetResponseDetailsWithPrivateDataCreatedOnBuddyRequest, inviteUrl, true, null)
		def newNickname = "Bobby"
		def newPassword = "B o b"
		def updatedBobJson = bob.convertToJson()
		updatedBobJson.yonaPassword = newPassword
		updatedBobJson.nickname = newNickname
		User updatedBob = appService.updateUserCreatedOnBuddyRequest(appService.&assertUserUpdateResponseDetails, new User(updatedBobJson), inviteUrl)
		updatedBob = appService.confirmMobileNumber(appService.&assertResponseStatusSuccess, updatedBob)
		def acceptUrl = appService.fetchBuddyConnectRequestMessage(updatedBob).acceptUrl

		when:
		def response = appService.postMessageActionWithPassword(acceptUrl, ["message" : "Yes, great idea!"], updatedBob.password)

		then:
		response.status == 200
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
		def mobileNumberBob = "+$timestamp"
		def inviteUrl = buildInviteUrl(sendBuddyRequestForBob(richard, mobileNumberBob))
		def bob = appService.getUser(appService.&assertUserGetResponseDetailsWithPrivateDataCreatedOnBuddyRequest, inviteUrl, true, null)
		def newNickname = "Bobby"
		def newPassword = "B o b"
		def updatedBobJson = bob.convertToJson()
		updatedBobJson.yonaPassword = newPassword
		updatedBobJson.nickname = newNickname
		User updatedBob = appService.updateUserCreatedOnBuddyRequest(appService.&assertUserUpdateResponseDetails, new User(updatedBobJson), inviteUrl)
		updatedBob = appService.confirmMobileNumber(appService.&assertResponseStatusSuccess, updatedBob)
		def acceptUrl = appService.fetchBuddyConnectRequestMessage(updatedBob).acceptUrl
		appService.postMessageActionWithPassword(acceptUrl, ["message" : "Yes, great idea!"], updatedBob.password)

		when:
		def response = appService.getMessages(richard)

		then:
		response.status == 200
		def buddyConnectResponseMessages = response.responseData._embedded."yona:messages".findAll
		{ it."@type" == "BuddyConnectResponseMessage" }
		buddyConnectResponseMessages[0]._links?."yona:user"?.href == bob.url
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
		def mobileNumberBob = "+$timestamp"
		def inviteUrl = buildInviteUrl(sendBuddyRequestForBob(richard, mobileNumberBob))
		def bob = appService.getUser(appService.&assertUserGetResponseDetailsWithPrivateDataCreatedOnBuddyRequest, inviteUrl, true, null)
		def newNickname = "Bobby"
		def newPassword = "B o b"
		def updatedBobJson = bob.convertToJson()
		updatedBobJson.yonaPassword = newPassword
		updatedBobJson.nickname = newNickname
		User updatedBob = appService.updateUserCreatedOnBuddyRequest(appService.&assertUserUpdateResponseDetails, new User(updatedBobJson), inviteUrl)
		updatedBob = appService.confirmMobileNumber(appService.&assertResponseStatusSuccess, updatedBob)
		def acceptUrl = appService.fetchBuddyConnectRequestMessage(updatedBob).acceptUrl
		appService.postMessageActionWithPassword(acceptUrl, ["message" : "Yes, great idea!"], updatedBob.password)
		def processUrl = appService.fetchBuddyConnectResponseMessage(richard).processUrl
		appService.postMessageActionWithPassword(processUrl, [ : ], richard.password)

		when:
		analysisService.postToAnalysisEngine(richard, ["news/media"], "http://www.refdag.nl")

		then:
		def getMessagesRichardResponse = appService.getMessages(richard)
		getMessagesRichardResponse.status == 200
		def richardGoalConflictMessages = getMessagesRichardResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		richardGoalConflictMessages.size() == 1
		richardGoalConflictMessages[0].nickname == "RQ (me)"
		richardGoalConflictMessages[0]._links."yona:activityCategory".href == NEWS_ACT_CAT_URL
		richardGoalConflictMessages[0].url == "http://www.refdag.nl"

		def getMessagesBobResponse = appService.getMessages(updatedBob)
		getMessagesBobResponse.status == 200
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
		def mobileNumberBob = "+$timestamp"
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
		def richard = addRichard()
		def inviteUrl = buildInviteUrl(sendBuddyRequestForBob(richard, "+$timestamp"))

		when:
		def response = appService.getResource(YonaServer.stripQueryString(inviteUrl), [:], ["tempPassword": "hack", "includePrivateData": "true"])

		then:
		response.status == 400
		response.responseData.code == "error.decrypting.data"

		cleanup:
		appService.deleteUser(richard)
		// TODO: How to delete the invited user?
	}

	def 'Hacking attempt: Try to update Bob Dunn with an invalid temp password'()
	{
		given:
		def richard = addRichard()
		def inviteUrl = buildInviteUrl(sendBuddyRequestForBob(richard, "+$timestamp"))

		when:
		def response = appService.updateResource(YonaServer.stripQueryString(inviteUrl), """{
				"firstName":"Richard",
				"lastName":"Quin",
				"nickname":"RQ",
				"mobileNumber":"+$timestamp"
			}""", [:], ["tempPassword": "hack"])

		then:
		response.status == 400
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
		def response = appService.getResource(richard.url, [:], ["tempPassword": richard.password, "includePrivateData": "true"])

		then:
		response.status == 400
		response.responseData.code == "error.user.not.created.on.buddy.request"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Hacking attempt: Try to get a user created on buddy request with normal Yona password header'()
	{
		given:
		def richard = addRichard()
		def mobileNumberBob = "+$timestamp"
		def responseAddBuddy = sendBuddyRequestForBob(richard, mobileNumberBob)
		def bobUrl = responseAddBuddy.responseData._embedded."yona:user"._links.self.href

		when:
		def response = appService.getUser(bobUrl, true, dummyTempPassword)

		then:
		response.status == 400
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
		def response = appService.updateResource(richard.url, """{
				"firstName":"Richard",
				"lastName":"Quin",
				"nickname":"RQ",
				"mobileNumber":"+$timestamp"
			}""", [:], ["tempPassword": "hack"])

		then:
		response.status == 400
		response.responseData.code == "error.user.not.created.on.buddy.request"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Overwrite user created on buddy request'()
	{
		given:
		def richard = addRichard()
		def mobileNumberBob = "+$timestamp"
		def inviteUrl = buildInviteUrl(sendBuddyRequestForBob(richard, mobileNumberBob))

		when:
		def response = appService.requestOverwriteUser(mobileNumberBob)

		then:
		response.status == 200

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
		getMessagesResponse.status == 200
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
		def mobileNumberBob = "+$timestamp"
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
		getMessagesRichardResponse.status == 200
		def richardGoalConflictMessages = getMessagesRichardResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		richardGoalConflictMessages.size() == 1
		richardGoalConflictMessages[0].nickname == "RQ (me)"
		richardGoalConflictMessages[0]._links."yona:activityCategory".href == NEWS_ACT_CAT_URL
		richardGoalConflictMessages[0].url == "http://www.refdag.nl"

		def getMessagesBobResponse = appService.getMessages(bob)
		getMessagesBobResponse.status == 200
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
		response.status == 400
		response.responseData.code == "error.user.exists.created.on.buddy.request"
	}

	def assertUserOverwriteResponseDetails(def response)
	{
		appService.assertResponseStatusCreated(response)
		appService.assertUserWithPrivateData(response.responseData)
	}

	def sendBuddyRequestForBob(User user, String mobileNumber)
	{
		appService.yonaServer.createResourceWithPassword(user.url + "/buddies/", """{
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
		response.responseData._embedded."yona:user"._links.self.href + "?tempPassword=" + dummyTempPassword
	}
}
