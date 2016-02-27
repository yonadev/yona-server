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
	def 'Richard cannot create a buddy request before confirming his own mobile number'()
	{
		given:
		def richard = appService.addUser(appService.&assertUserCreationResponseDetails, "R i c h a r d", "Richard", "Quinn", "RQ",
				"+$timestamp", ["Nexus 6"])

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
		response.responseData._embedded.user.firstName == "Bob"
		response.responseData._links.self.href.startsWith(richard.url)
		response.responseData.userCreatedInviteURL

		cleanup:
		appService.deleteUser(richard)
		// TODO: How to delete the invited user?
	}

	def 'Bob downloads the app and uses it to open the link sent in the e-mail and load the prefilled data provided by Richard'()
	{
		given:
		def richard = addRichard()
		def mobileNumberBob = "+$timestamp"
		def inviteURL = sendBuddyRequestForBob(richard, mobileNumberBob).responseData.userCreatedInviteURL

		when:
		def bob = appService.getUser(appService.&assertUserGetResponseDetailsPublicDataAndVpnProfile, inviteURL, true, null)

		then:
		bob.firstName == "Bob"
		bob.lastName == "Dunn"
		bob.mobileNumber == mobileNumberBob
		bob.mobileNumberConfirmationUrl != null

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Bob adjusts data and submits; app saves with the device password'()
	{
		given:
		def richard = addRichard()
		def mobileNumberBob = "+$timestamp"
		def inviteURL = sendBuddyRequestForBob(richard, mobileNumberBob).responseData.userCreatedInviteURL
		def bob = appService.getUser(appService.&assertUserGetResponseDetailsPublicDataAndVpnProfile, inviteURL, true, null)

		when:
		def newNickname = "Bobby"
		def newPassword = "B o b"
		def updatedBobJson = bob.convertToJSON()
		updatedBobJson.nickname = newNickname
		updatedBobJson.devices = ["iPhone 6"]
		def response = appService.updateUser(inviteURL, updatedBobJson, newPassword)

		then:
		response.status == 200
		response.responseData.firstName == bob.firstName
		response.responseData.lastName == bob.lastName
		response.responseData.mobileNumber == bob.mobileNumber
		response.responseData.nickname == newNickname
		response.responseData.devices.size() == 1
		response.responseData.devices[0] == "iPhone 6"
		response.responseData._embedded.goals._embedded.budgetGoals
		response.responseData._embedded.goals._embedded.budgetGoals.size() == 1 //mandatory goal
		!(response.responseData._links.self.href ==~ /tempPassword/)
		response.responseData.mobileNumberConfirmationCode

		def getUserResponse = appService.getUser(inviteURL, true, null)
		getUserResponse.status == 400
		getUserResponse.responseData.code == "error.decrypting.data"

		def bobFromGetAfterUpdate = appService.getUser(appService.&assertUserGetResponseDetailsPublicDataAndVpnProfile, bob.url, true, newPassword)
		bobFromGetAfterUpdate.firstName == bob.firstName
		bobFromGetAfterUpdate.lastName == bob.lastName
		bobFromGetAfterUpdate.mobileNumber == bob.mobileNumber
		bobFromGetAfterUpdate.nickname == newNickname
		bobFromGetAfterUpdate.devices.size() == 1
		bobFromGetAfterUpdate.devices[0] == "iPhone 6"
		bobFromGetAfterUpdate.goals.size() == 1 //mandatory goal
		bobFromGetAfterUpdate.url

		def getMessagesResponse = appService.yonaServer.getResourceWithPassword(bobFromGetAfterUpdate.url + "/messages/", bobFromGetAfterUpdate.password)
		getMessagesResponse.status == 400
		getMessagesResponse.responseData.code == "error.mobile.number.not.confirmed"

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob.url, newPassword)
	}

	def 'Bob updates his user information saved with the device password'()
	{
		given:
		def richard = addRichard()
		def mobileNumberBob = "+$timestamp"
		def inviteURL = sendBuddyRequestForBob(richard, mobileNumberBob).responseData.userCreatedInviteURL
		def bob = appService.getUser(appService.&assertUserGetResponseDetailsPublicDataAndVpnProfile, inviteURL, true, null)
		def newNickname = "Bobby"
		def newPassword = "B o b"
		def updatedBobJson = bob.convertToJSON()
		updatedBobJson.nickname = newNickname
		updatedBobJson.devices = ["iPhone 6"]
		appService.updateUser(inviteURL, updatedBobJson, newPassword)
		def bobFromGetAfterUpdate = appService.getUser(appService.&assertUserGetResponseDetailsPublicDataAndVpnProfile, bob.url, true, newPassword)

		when:
		def againChangedNickname = "Robert"
		def againUpdatedBobJson = bobFromGetAfterUpdate.convertToJSON()
		againUpdatedBobJson.nickname = againChangedNickname
		def againUpdatedBob = appService.updateUser(appService.&assertUserUpdateResponseDetails, new User(againUpdatedBobJson, newPassword))

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
		def inviteURL = sendBuddyRequestForBob(richard, mobileNumberBob).responseData.userCreatedInviteURL
		def bob = appService.getUser(appService.&assertUserGetResponseDetailsPublicDataAndVpnProfile, inviteURL, true, null)
		def newNickname = "Bobby"
		def newPassword = "B o b"
		def updatedBobJson = bob.convertToJSON()
		updatedBobJson.nickname = newNickname
		updatedBobJson.devices = ["iPhone 6"]
		def updatedBob = appService.updateUser(appService.&assertUserUpdateResponseDetails, new User(updatedBobJson, newPassword), inviteURL)

		when:
		appService.confirmMobileNumber(appService.&assertResponseStatusSuccess, updatedBob)

		then:
		def getMessagesResponse = appService.getMessages(updatedBob)
		getMessagesResponse.status == 200

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(updatedBob)
	}

	def 'Bob accepts Richard\'s buddy request'()
	{
		given:
		def richard = addRichard()
		def mobileNumberBob = "+$timestamp"
		def inviteURL = sendBuddyRequestForBob(richard, mobileNumberBob).responseData.userCreatedInviteURL
		def bob = appService.getUser(appService.&assertUserGetResponseDetailsPublicDataAndVpnProfile, inviteURL, true, null)
		def newNickname = "Bobby"
		def newPassword = "B o b"
		def updatedBobJson = bob.convertToJSON()
		updatedBobJson.nickname = newNickname
		updatedBobJson.devices = ["iPhone 6"]
		def updatedBob = appService.updateUser(appService.&assertUserUpdateResponseDetails, new User(updatedBobJson, newPassword), inviteURL)
		appService.confirmMobileNumber(appService.&assertResponseStatusSuccess, updatedBob)
		def acceptURL = appService.fetchBuddyConnectRequestMessage(updatedBob).acceptURL

		when:
		def response = appService.postMessageActionWithPassword(acceptURL, ["message" : "Yes, great idea!"], newPassword)

		then:
		response.status == 200
		def bobWithBuddy = appService.getUser(appService.&assertUserGetResponseDetailsPublicDataAndVpnProfile, updatedBob.url, true, updatedBob.password)
		bobWithBuddy.buddies != null
		bobWithBuddy.buddies.size() == 1
		bobWithBuddy.buddies[0].user.firstName == "Richard"
		bobWithBuddy.buddies[0].sendingStatus == "ACCEPTED"
		bobWithBuddy.buddies[0].receivingStatus == "ACCEPTED"

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(updatedBob)
	}

	def 'Richard processes Bob\'s buddy acceptance'()
	{
		given:
		def richard = addRichard()
		def mobileNumberBob = "+$timestamp"
		def inviteURL = sendBuddyRequestForBob(richard, mobileNumberBob).responseData.userCreatedInviteURL
		def bob = appService.getUser(appService.&assertUserGetResponseDetailsPublicDataAndVpnProfile, inviteURL, true, null)
		def newNickname = "Bobby"
		def newPassword = "B o b"
		def updatedBobJson = bob.convertToJSON()
		updatedBobJson.nickname = newNickname
		updatedBobJson.devices = ["iPhone 6"]
		def updatedBob = appService.updateUser(appService.&assertUserUpdateResponseDetails, new User(updatedBobJson, newPassword), inviteURL)
		appService.confirmMobileNumber(appService.&assertResponseStatusSuccess, updatedBob)
		def acceptURL = appService.fetchBuddyConnectRequestMessage(updatedBob).acceptURL
		appService.postMessageActionWithPassword(acceptURL, ["message" : "Yes, great idea!"], newPassword)

		when:
		def processURL = appService.fetchBuddyConnectResponseMessage(richard).processURL
		def response = appService.postMessageActionWithPassword(processURL, [ : ], richard.password)

		then:
		response.status == 200
		response.responseData.properties.status == "done"

		def richardWithBuddy = appService.getUser(appService.&assertUserGetResponseDetailsPublicDataAndVpnProfile, richard.url, true, richard.password)
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
		def inviteURL = sendBuddyRequestForBob(richard, mobileNumberBob).responseData.userCreatedInviteURL
		def bob = appService.getUser(appService.&assertUserGetResponseDetailsPublicDataAndVpnProfile, inviteURL, true, null)
		def newNickname = "Bobby"
		def newPassword = "B o b"
		def updatedBobJson = bob.convertToJSON()
		updatedBobJson.nickname = newNickname
		updatedBobJson.devices = ["iPhone 6"]
		def updatedBob = appService.updateUser(appService.&assertUserUpdateResponseDetails, new User(updatedBobJson, newPassword), inviteURL)
		appService.confirmMobileNumber(appService.&assertResponseStatusSuccess, updatedBob)
		def acceptURL = appService.fetchBuddyConnectRequestMessage(updatedBob).acceptURL
		appService.postMessageActionWithPassword(acceptURL, ["message" : "Yes, great idea!"], newPassword)
		def processURL = appService.fetchBuddyConnectResponseMessage(richard).processURL
		appService.postMessageActionWithPassword(processURL, [ : ], richard.password)

		when:
		analysisService.postToAnalysisEngine(richard, ["news/media"], "http://www.refdag.nl")

		then:
		def getMessagesRichardResponse = appService.getMessages(richard)
		getMessagesRichardResponse.status == 200
		def richardGoalConflictMessages = getMessagesRichardResponse.responseData._embedded.messages.findAll{ it."@type" == "GoalConflictMessage"}
		richardGoalConflictMessages.size() == 1
		richardGoalConflictMessages[0].nickname == "<self>"
		richardGoalConflictMessages[0].activityCategoryName == "news"
		richardGoalConflictMessages[0].url == "http://www.refdag.nl"

		def getMessagesBobResponse = appService.getMessages(updatedBob)
		getMessagesBobResponse.status == 200
		def bobGoalConflictMessages = getMessagesBobResponse.responseData._embedded.messages.findAll{ it."@type" == "GoalConflictMessage"}
		bobGoalConflictMessages.size() == 1
		bobGoalConflictMessages[0].nickname == richard.nickname
		bobGoalConflictMessages[0].activityCategoryName == "news"
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
		def john = appService.addUser(this.&assertUserCreationFailedBecauseOfDuplicate, "J o h n", "John", "Doe", "JD",
				mobileNumberBob, ["Nokia 6310"])

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
		def inviteURL = sendBuddyRequestForBob(richard, "+$timestamp").responseData.userCreatedInviteURL

		when:
		def response = appService.getResource(YonaServer.stripQueryString(inviteURL), [:], ["tempPassword": "hack", "includePrivateData": "true"])

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
		def inviteURL = sendBuddyRequestForBob(richard, "+$timestamp").responseData.userCreatedInviteURL

		when:
		def response = appService.updateResource(YonaServer.stripQueryString(inviteURL), """{
				"firstName":"Richard",
				"lastName":"Quin",
				"nickname":"RQ",
				"mobileNumber":"+$timestamp",
				"devices":[
					"Nexus 6"
				]
			}""", ["Yona-Password": "New password"], ["tempPassword": "hack"])

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
		def richard = addRichard()

		when:
		def response = appService.getResource(richard.url, [:], ["tempPassword": "hack", "includePrivateData": "true"])

		then:
		response.status == 400
		response.responseData.code == "error.decrypting.data"

		cleanup:
		appService.deleteUser(richard)
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
				"mobileNumber":"+$timestamp",
				"devices":[
					"Nexus 6"
				]
			}""", ["Yona-Password": "New password"], ["tempPassword": "hack"])

		then:
		response.status == 400
		response.responseData.code == "error.user.not.created.on.buddy.request"

		cleanup:
		appService.deleteUser(richard)
	}

	def assertUserCreationFailedBecauseOfDuplicate(response)
	{
		response.status == 400
		response.responseData.code == "error.user.exists.created.on.buddy.request"
	}

	def sendBuddyRequestForBob(User user, String mobileNumber)
	{
		appService.requestBuddy(user.url, """{
			"_embedded":{
				"user":{
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
}
