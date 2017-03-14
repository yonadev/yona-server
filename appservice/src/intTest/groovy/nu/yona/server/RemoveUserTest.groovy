/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import groovy.json.*
import nu.yona.server.test.Goal
import nu.yona.server.test.TimeZoneGoal
import nu.yona.server.test.User

class RemoveUserTest extends AbstractAppServiceIntegrationTest
{
	def 'Delete account'()
	{
		given:
		def richard = addRichard()

		when:
		def response = appService.deleteUser(richard, "Goodbye friends! I deinstalled the Internet")

		then:
		response.status == 200
	}

	def 'Delete and recreate account'()
	{
		given:
		def richard = addRichard()
		appService.deleteUser(richard, "Goodbye friends! I deinstalled the Internet")

		when:
		def newRichard = appService.addUser(appService.&assertUserCreationResponseDetails, richard.password, richard.firstName, richard.lastName,
				richard.nickname, richard.mobileNumber)

		then:
		newRichard

		cleanup:
		appService.deleteUser(newRichard)
	}

	def 'Remove Richard while being a buddy of Bob and verify that Bob receives a buddy disconnect message'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		def richard = richardAndBob.richard
		def bob = richardAndBob.bob
		analysisService.postToAnalysisEngine(bob, ["Gambling"], "http://www.poker.com")
		analysisService.postToAnalysisEngine(richard, "news/media", "http://www.refdag.nl")

		when:
		def message = "Goodbye friends! I deinstalled the Internet"
		appService.deleteUser(richard, message)

		then:
		def buddies = appService.getBuddies(bob)
		buddies.size() == 0

		def getMessagesResponse = appService.getMessages(bob)
		getMessagesResponse.status == 200
		getMessagesResponse.responseData._embedded
		getMessagesResponse.responseData._embedded."yona:messages".size() == 2

		def buddyDisconnectMessages = getMessagesResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "BuddyDisconnectMessage"}
		buddyDisconnectMessages.size() == 1
		def disconnectMessage = buddyDisconnectMessages[0]
		disconnectMessage.reason == "USER_ACCOUNT_DELETED"
		disconnectMessage.message == message
		disconnectMessage.nickname == richard.nickname
		disconnectMessage._links.self.href.startsWith(YonaServer.stripQueryString(bob.messagesUrl))
		disconnectMessage._links."yona:process" == null // Processing happens automatically these days

		def goalConflictMessages = getMessagesResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		goalConflictMessages.size == 1
		goalConflictMessages[0].nickname == "BD (me)"
		goalConflictMessages[0]._links."yona:activityCategory".href == GAMBLING_ACT_CAT_URL
		goalConflictMessages[0].url =~ /poker/
		getMessagesResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "BuddyConnectRequestMessage"}.size() == 0

		cleanup:
		appService.deleteUser(bob)
	}

	def 'Remove Richard with pending buddy request from Bob and verify that Bob receives a reject message'()
	{
		given:
		def richard = addRichard()
		def bob = addBob()
		richard.emailAddress = "richard@quinn.com"
		appService.sendBuddyConnectRequest(bob, richard)

		when:
		def message = "Goodbye friends! I deinstalled the Internet"
		appService.deleteUser(richard, message)

		then:
		def buddies = appService.getBuddies(bob)
		buddies.size() == 0

		def getMessagesResponse = appService.getMessages(bob)
		getMessagesResponse.status == 200
		getMessagesResponse.responseData._embedded
		getMessagesResponse.responseData._embedded."yona:messages".size() == 1

		def buddyConnectResponseMessages = getMessagesResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "BuddyConnectResponseMessage"}
		buddyConnectResponseMessages.size() == 1
		def buddyConnectResponseMessage = buddyConnectResponseMessages[0]
		buddyConnectResponseMessage.message == "User account was deleted"
		buddyConnectResponseMessage.nickname == "$richard.firstName $richard.lastName"
		buddyConnectResponseMessage._links.self.href.startsWith(YonaServer.stripQueryString(bob.messagesUrl))
		buddyConnectResponseMessage._links."yona:process" == null // Processing happens automatically these days

		when:
		// Test whether Richard can be removed after he again established a buddy relationship with Bob
		def newRichard = addRichard()
		bob.emailAddress = "bob@dunn.net"
		appService.makeBuddies(newRichard, bob)
		
		then:
		appService.deleteUser(newRichard, "Sorry, going again").status == 200

		cleanup:
		appService.deleteUser(bob)
	}

	def 'Remove Richard with pending buddy request to Bob and verify buddy request is removed'()
	{
		given:
		def richard = addRichard()
		def bob = addBob()
		bob.emailAddress = "bob@dunn.net"
		appService.sendBuddyConnectRequest(richard, bob)

		when:
		def message = "Goodbye friends! I deinstalled the Internet"
		appService.deleteUser(richard, message)

		then:
		def getMessagesResponse = appService.getMessages(bob)
		getMessagesResponse.status == 200
		getMessagesResponse.responseData._embedded == null

		def buddies = appService.getBuddies(bob)
		buddies.size() == 0

		cleanup:
		appService.deleteUser(bob)
	}

	def 'Remove Richard while being a buddy of Bob and verify that Bob can move on with everything'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		def richard = richardAndBob.richard
		def bob = richardAndBob.bob
		setGoalCreationTime(richard, NEWS_ACT_CAT_URL, "W-1 Mon 02:18")
		setGoalCreationTime(bob, NEWS_ACT_CAT_URL, "W-1 Mon 02:18")
		richard = appService.reloadUser(richard)
		bob = appService.reloadUser(bob)
		Goal budgetGoalNewsBuddyRichard = bob.buddies[0].findActiveGoal(NEWS_ACT_CAT_URL)
		Goal budgetGoalNewsBuddyBob = richard.buddies[0].findActiveGoal(NEWS_ACT_CAT_URL)
		//insert some messages
		//goal conflict
		analysisService.postToAnalysisEngine(richard, "news/media", "http://www.refdag.nl")
		//goal change
		appService.addGoal(appService.&assertResponseStatusCreated, richard, TimeZoneGoal.createInstance(SOCIAL_ACT_CAT_URL, ["11:00-12:00"].toArray()), "Going to restrict my social time!")
		//activity comment at activity of Richard
		def bobResponseDetailsRichard = appService.getDayActivityDetails(bob, bob.buddies[0], budgetGoalNewsBuddyRichard, 1, "Tue")
		appService.yonaServer.createResourceWithPassword(bobResponseDetailsRichard.responseData._links."yona:addComment".href, """{"message": "Hi Richard, everything OK?"}""", bob.password)
		//activity comment from Richard
		def richardResponseDetailsBob = appService.getDayActivityDetails(richard, richard.buddies[0], budgetGoalNewsBuddyBob, 1, "Tue")
		appService.yonaServer.createResourceWithPassword(richardResponseDetailsBob.responseData._links."yona:addComment".href, """{"message": "Hi Bob, nice activity!"}""", richard.password)
		//buddy info change
		def updatedRichardJson = richard.convertToJson()
		updatedRichardJson.nickname = "Richardo"
		richard = appService.updateUser(appService.&assertUserUpdateResponseDetails, new User(updatedRichardJson))

		when:
		def message = "Goodbye friends! I deinstalled the Internet"
		appService.deleteUser(richard, message)

		then:
		analysisService.postToAnalysisEngine(bob, ["Gambling"], "http://www.poker.com")
		analysisService.postToAnalysisEngine(richard, "news/media", "http://www.refdag.nl")

		def getMessagesResponse = appService.getMessages(bob)
		getMessagesResponse.status == 200
		getMessagesResponse.responseData._embedded
		getMessagesResponse.responseData._embedded."yona:messages".size() == 2

		def buddyDisconnectMessages = getMessagesResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "BuddyDisconnectMessage"}
		buddyDisconnectMessages.size() == 1

		def disconnectMessage = buddyDisconnectMessages[0]
		disconnectMessage.reason == "USER_ACCOUNT_DELETED"
		disconnectMessage.message == message
		disconnectMessage.nickname == richard.nickname
		disconnectMessage._links.self.href.startsWith(YonaServer.stripQueryString(bob.messagesUrl))
		disconnectMessage._links."yona:process" == null // Processing happens automatically these days

		def goalConflictMessages = getMessagesResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		goalConflictMessages.size == 1
		goalConflictMessages[0].nickname == "BD (me)"
		goalConflictMessages[0]._links."yona:activityCategory".href == GAMBLING_ACT_CAT_URL
		goalConflictMessages[0].url =~ /poker/
		getMessagesResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "BuddyConnectRequestMessage"}.size() == 0

		def goalChangeMessages = getMessagesResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalChangeMessage"}
		goalChangeMessages.size == 0

		def activityCommentMessages = getMessagesResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "ActivityCommentMessage"}
		activityCommentMessages.size == 0

		def buddyInfoChangeMessages = getMessagesResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "BuddyInfoChangeMessage"}
		buddyInfoChangeMessages.size == 0

		def buddies = appService.getBuddies(bob)
		buddies.size() == 0

		cleanup:
		appService.deleteUser(bob)
	}

	def 'Conflicts for Bob are still processed after the unsubscribe of Richard'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		def richard = richardAndBob.richard
		def bob = richardAndBob.bob
		def message = "Goodbye friends! I deinstalled the Internet"
		appService.deleteUser(richard, message)
		def getResponse = appService.getMessages(bob)
		assert getResponse.status == 200
		assert getResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "BuddyDisconnectMessage"}[0]._links."yona:process" == null // Processing happens automatically these days

		when:
		def response = analysisService.postToAnalysisEngine(bob, ["Gambling"], "http://www.poker.com")

		then:
		response.status == 200
		def getMessagesResponse = appService.getMessages(bob)
		getMessagesResponse.status == 200
		getMessagesResponse.responseData._embedded
		def buddyDisconnectMessages = getMessagesResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "BuddyDisconnectMessage"}
		buddyDisconnectMessages[0].reason == "USER_ACCOUNT_DELETED"
		buddyDisconnectMessages[0].message == message
		buddyDisconnectMessages[0].nickname == richard.nickname
		buddyDisconnectMessages[0]._links.self.href.startsWith(YonaServer.stripQueryString(bob.messagesUrl))
		!buddyDisconnectMessages[0]._links."yona:process"

		cleanup:
		appService.deleteUser(bob)
	}
}
