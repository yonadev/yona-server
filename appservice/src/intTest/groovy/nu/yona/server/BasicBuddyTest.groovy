/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import java.time.Duration
import java.time.ZonedDateTime

import groovy.json.*
import nu.yona.server.test.AppService
import nu.yona.server.test.Buddy
import nu.yona.server.test.Goal
import nu.yona.server.test.User

class BasicBuddyTest extends AbstractAppServiceIntegrationTest
{
	def 'Hacking attempt: Try to request one-way connection'()
	{
		given:
		User richard = addRichard()
		User bob = addBob()

		when:
		def response = appService.requestBuddy(richard, """{
						"_embedded":{
							"yona:user":{
								"firstName":"Bob",
								"lastName":"Dun",
								"emailAddress":"bob@dunn.net",
								"mobileNumber":"$bob.mobileNumber"
							}
						},
						"message":"Would you like to be my buddy?",
						"sendingStatus":"NOT_REQUESTED",
						"receivingStatus":"REQUESTED"
					}""", richard.password)

		then:
		response.status == 400
		response.responseData.code == "error.buddy.only.twoway.buddies.allowed"

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Richard requests Bob to become his buddy'()
	{
		given:
		User richard = addRichard()
		User bob = addBob()
		bob.emailAddress = "bob@dunn.net"

		when:
		ZonedDateTime buddyRequestTime = YonaServer.now
		def response = appService.sendBuddyConnectRequest(richard, bob)

		then:
		response.status == 201
		response.responseData._embedded."yona:user".firstName == "Bob"
		response.responseData._links."yona:user" == null
		response.responseData._links.self.href.startsWith(richard.url)

		User richardWithBuddy = appService.reloadUser(richard)
		richardWithBuddy.buddies != null
		richardWithBuddy.buddies.size() == 1
		richardWithBuddy.buddies[0].user.firstName == bob.firstName
		// goals and devices should not be embedded before accept
		richardWithBuddy.buddies[0].goals == null
		richardWithBuddy.buddies[0].user.goals == null
		richardWithBuddy.buddies[0].user.devices == null
		richardWithBuddy.buddies[0].sendingStatus == "REQUESTED"
		richardWithBuddy.buddies[0].receivingStatus == "REQUESTED"
		AppService.assertDateTimeFormat(richardWithBuddy.buddies[0].lastStatusChangeTime)
		AppService.assertEquals(richardWithBuddy.buddies[0].lastStatusChangeTime, buddyRequestTime)

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Bob finds the buddy request'()
	{
		given:
		User richard = addRichard()
		User bob = addBob()
		bob.emailAddress = "bob@dunn.net"
		appService.sendBuddyConnectRequest(richard, bob)

		when:
		def response = appService.getMessages(bob)

		then:
		response.status == 200
		response.responseData._embedded."yona:messages".size() == 1
		def buddyConnectRequestMessages = response.responseData._embedded."yona:messages".findAll
		{ it."@type" == "BuddyConnectRequestMessage" }
		buddyConnectRequestMessages.size() == 1
		buddyConnectRequestMessages[0].nickname == richard.nickname
		AppService.assertEquals(buddyConnectRequestMessages[0].creationTime, YonaServer.now)
		buddyConnectRequestMessages[0].status == "REQUESTED"
		buddyConnectRequestMessages[0]._embedded."yona:user".firstName == "Richard"
		buddyConnectRequestMessages[0]._links."yona:user" == null
		buddyConnectRequestMessages[0]._links.self.href.startsWith(YonaServer.stripQueryString(bob.messagesUrl))
		buddyConnectRequestMessages[0]._links."yona:accept".href.startsWith(buddyConnectRequestMessages[0]._links.self.href)

		assertMarkReadUnread(bob, buddyConnectRequestMessages[0])

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Bob accepts Richard\'s buddy request'()
	{
		given:
		User richard = addRichard()
		User bob = addBob()
		bob.emailAddress = "bob@dunn.net"
		appService.sendBuddyConnectRequest(richard, bob)
		def connectRequestMessage = appService.fetchBuddyConnectRequestMessage(bob)
		def acceptUrl = connectRequestMessage.acceptUrl

		when:
		def response = appService.postMessageActionWithPassword(acceptUrl, ["message" : "Yes, great idea!"], bob.password)

		then:
		response.status == 200
		response.responseData.properties.status == "done"
		response.responseData._embedded."yona:affectedMessages".size() == 1
		response.responseData._embedded."yona:affectedMessages"[0]._links.self.href == connectRequestMessage.selfUrl
		response.responseData._embedded."yona:affectedMessages"[0].status == "ACCEPTED"
		response.responseData._embedded."yona:affectedMessages"[0]._links."yona:accept" == null
		response.responseData._embedded."yona:affectedMessages"[0]._links."yona:reject" == null

		def buddies = appService.getBuddies(bob)
		buddies.size() == 1
		buddies[0].user.firstName == richard.firstName
		buddies[0].nickname == richard.nickname
		buddies[0].sendingStatus == "ACCEPTED"
		buddies[0].receivingStatus == "ACCEPTED"

		def bobWithBuddy = appService.reloadUser(bob)
		bobWithBuddy.buddies != null
		bobWithBuddy.buddies.size() == 1
		bobWithBuddy.buddies[0].user.firstName == richard.firstName
		bobWithBuddy.buddies[0].goals.size() == 2
		bobWithBuddy.buddies[0].sendingStatus == "ACCEPTED"
		bobWithBuddy.buddies[0].receivingStatus == "ACCEPTED"

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Richard finds Bob\'s buddy connect response'()
	{
		given:
		User richard = addRichard()
		User bob = addBob()
		bob.emailAddress = "bob@dunn.net"
		appService.sendBuddyConnectRequest(richard, bob)
		def acceptUrl = appService.fetchBuddyConnectRequestMessage(bob).acceptUrl
		appService.postMessageActionWithPassword(acceptUrl, ["message" : "Yes, great idea!"], bob.password)

		when:
		sleep(100) // So we are sure the request time differs from the processing time
		ZonedDateTime buddyResponseProcessTime = YonaServer.now
		def response = appService.getMessages(richard)

		then:
		response.status == 200
		def buddyConnectResponseMessages = response.responseData._embedded."yona:messages".findAll
		{ it."@type" == "BuddyConnectResponseMessage" }
		buddyConnectResponseMessages[0]._links?."yona:user"?.href == bob.url
		buddyConnectResponseMessages[0]._embedded?."yona:user" == null
		buddyConnectResponseMessages[0].nickname == bob.nickname
		AppService.assertEquals(buddyConnectResponseMessages[0].creationTime, YonaServer.now)
		buddyConnectResponseMessages[0].status == "ACCEPTED"
		buddyConnectResponseMessages[0]._links.self.href.startsWith(YonaServer.stripQueryString(richard.messagesUrl))
		buddyConnectResponseMessages[0]._links."yona:process" == null // Processing happens automatically these days

		assertMarkReadUnread(richard, buddyConnectResponseMessages[0])

		def buddies = appService.getBuddies(richard)
		buddies.size() == 1
		buddies[0].user.firstName == bob.firstName
		buddies[0].nickname == bob.nickname
		buddies[0].sendingStatus == "ACCEPTED"
		buddies[0].receivingStatus == "ACCEPTED"
		buddies[0].url == buddyConnectResponseMessages[0]._links."yona:buddy".href
		buddies[0].user.nickname == bob.nickname

		def richardWithBuddy = appService.reloadUser(richard)
		richardWithBuddy.buddies != null
		richardWithBuddy.buddies.size() == 1
		richardWithBuddy.buddies[0].user.firstName == bob.firstName
		richardWithBuddy.buddies[0].user.nickname == bob.nickname
		richardWithBuddy.buddies[0].goals.size() == 2
		richardWithBuddy.buddies[0].nickname == bob.nickname
		richardWithBuddy.buddies[0].sendingStatus == "ACCEPTED"
		richardWithBuddy.buddies[0].receivingStatus == "ACCEPTED"
		AppService.assertDateTimeFormat(richardWithBuddy.buddies[0].lastStatusChangeTime)
		AppService.assertEquals(richardWithBuddy.buddies[0].lastStatusChangeTime, buddyResponseProcessTime)

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Richard and Bob can see each other\'s information'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob

		when:
		def buddiesRichard = appService.getBuddies(richard)
		def buddiesBob = appService.getBuddies(bob)

		then:
		buddiesRichard[0].goals.size() == 2
		buddiesRichard[0].user.goals.size() == 2
		buddiesRichard[0].user.devices.size() == 1
		buddiesBob[0].goals.size() == 2
		buddiesBob[0].user.goals.size() == 2
		buddiesBob[0].user.devices.size() == 1

		buddiesRichard[0].goals.each
		{
			assert appService.yonaServer.getResource(it.url, ["Yona-Password": richard.password]).status == 200
		}

		buddiesBob[0].goals.each
		{
			assert appService.yonaServer.getResource(it.url, ["Yona-Password": bob.password]).status == 200
		}

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Goal conflict of Richard is reported to Richard and Bob'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob
		ZonedDateTime now = YonaServer.now

		when:
		def response = analysisService.postToAnalysisEngine(richard, ["news/media"], "http://www.refdag.nl")

		then:
		response.status == 200
		def getMessagesRichardResponse = appService.getMessages(richard)
		getMessagesRichardResponse.status == 200
		def richardGoalConflictMessages = getMessagesRichardResponse.responseData._embedded."yona:messages".findAll
		{ it."@type" == "GoalConflictMessage" }
		richardGoalConflictMessages.size() == 1
		richardGoalConflictMessages[0].nickname == "RQ (me)"
		AppService.assertEquals(richardGoalConflictMessages[0].creationTime, now)
		AppService.assertEquals(richardGoalConflictMessages[0].activityStartTime, now)
		AppService.assertEquals(richardGoalConflictMessages[0].activityEndTime, now.plus(Duration.ofMinutes(1))) // Minimum duration 1 minute
		richardGoalConflictMessages[0]._links."yona:activityCategory".href == NEWS_ACT_CAT_URL
		richardGoalConflictMessages[0]._links."yona:buddy"?.href == null
		richardGoalConflictMessages[0].url == "http://www.refdag.nl"

		assertMarkReadUnread(richard, richardGoalConflictMessages[0])

		def getMessagesBobResponse = appService.getMessages(bob)
		getMessagesBobResponse.status == 200
		def bobGoalConflictMessages = getMessagesBobResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		bobGoalConflictMessages.size() == 1
		bobGoalConflictMessages[0].nickname == richard.nickname
		AppService.assertEquals(bobGoalConflictMessages[0].creationTime, now)
		AppService.assertEquals(bobGoalConflictMessages[0].activityStartTime, now)
		AppService.assertEquals(bobGoalConflictMessages[0].activityEndTime, now.plus(Duration.ofMinutes(1))) // Minimum duration 1 minute
		bobGoalConflictMessages[0]._links."yona:activityCategory".href == NEWS_ACT_CAT_URL
		bobGoalConflictMessages[0]._links."yona:buddy"?.href == bob.buddies[0].url
		bobGoalConflictMessages[0].url == null

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Goal conflict of Bob immediately after Bob\'s buddy acceptance is reported to Richard and Bob'()
	{
		given:
		User richard = addRichard()
		User bob = addBob()
		bob.emailAddress = "bob@dunn.net"
		appService.sendBuddyConnectRequest(richard, bob)
		def acceptUrl = appService.fetchBuddyConnectRequestMessage(bob).acceptUrl
		assert appService.postMessageActionWithPassword(acceptUrl, ["message" : "Yes, great idea!"], bob.password).status == 200

		when:
		def response = analysisService.postToAnalysisEngine(bob, ["news/media"], "http://www.refdag.nl")

		then:
		response.status == 200
		def getMessagesRichardResponse = appService.getMessages(richard)
		getMessagesRichardResponse.status == 200
		def richardGoalConflictMessages = getMessagesRichardResponse.responseData._embedded."yona:messages".findAll
		{ it."@type" == "GoalConflictMessage" }
		richardGoalConflictMessages.size() == 1
		richardGoalConflictMessages[0].nickname == bob.nickname

		def getMessagesBobResponse = appService.getMessages(bob)
		getMessagesBobResponse.status == 200
		def bobGoalConflictMessages = getMessagesBobResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		bobGoalConflictMessages.size() == 1
		bobGoalConflictMessages[0].nickname == "BD (me)"

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Two conflicts within the conflict interval are reported as one message for each person'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob

		when:
		analysisService.postToAnalysisEngine(richard, ["news/media"], "http://www.refdag.nl")
		analysisService.postToAnalysisEngine(richard, ["news/media"], "http://www.refdag.nl")

		then:
		def getMessagesRichardResponse = appService.getMessages(richard)
		getMessagesRichardResponse.status == 200
		getMessagesRichardResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}.size() == 1

		def getMessagesBobResponse = appService.getMessages(bob)
		getMessagesBobResponse.status == 200
		getMessagesBobResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}.size() == 1

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Goal conflict of Bob is reported to Richard and Bob'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob
		richard = appService.reloadUser(richard)
		bob = appService.reloadUser(bob)
		Goal goalBob = bob.findActiveGoal(GAMBLING_ACT_CAT_URL)
		Goal goalBuddyBob = richard.buddies[0].findActiveGoal(GAMBLING_ACT_CAT_URL)
		ZonedDateTime goalConflictTime = YonaServer.now

		when:
		analysisService.postToAnalysisEngine(bob, ["Gambling"], "http://www.poker.com")

		then:
		def getMessagesRichardResponse = appService.getMessages(richard)
		getMessagesRichardResponse.status == 200
		def richardGoalConflictMessages = getMessagesRichardResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		richardGoalConflictMessages.size() == 1
		richardGoalConflictMessages[0].nickname == bob.nickname
		richardGoalConflictMessages[0]._links."yona:activityCategory".href == GAMBLING_ACT_CAT_URL
		richardGoalConflictMessages[0].url == null
		// link to Bob's activity present
		def dayActivityDetailUrlRichard = richardGoalConflictMessages[0]._links."yona:dayDetails".href
		dayActivityDetailUrlRichard
		def dayActivityDetailRichard = appService.getResourceWithPassword(dayActivityDetailUrlRichard, richard.password)
		dayActivityDetailRichard.status == 200
		dayActivityDetailRichard.responseData.date == YonaServer.toIsoDateString(goalConflictTime)
		dayActivityDetailRichard.responseData._links."yona:goal".href == goalBuddyBob.url

		def getMessagesBobResponse = appService.getMessages(bob)
		getMessagesBobResponse.status == 200
		def bobGoalConflictMessages = getMessagesBobResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		bobGoalConflictMessages.size() == 1
		bobGoalConflictMessages[0].nickname == "BD (me)"
		bobGoalConflictMessages[0]._links."yona:activityCategory".href == GAMBLING_ACT_CAT_URL
		bobGoalConflictMessages[0].url == "http://www.poker.com"
		// link to own activity present
		def dayActivityDetailUrlBob = bobGoalConflictMessages[0]._links."yona:dayDetails".href
		dayActivityDetailUrlBob
		def dayActivityDetailBob = appService.getResourceWithPassword(dayActivityDetailUrlBob, bob.password)
		dayActivityDetailBob.status == 200
		dayActivityDetailBob.responseData.date == YonaServer.toIsoDateString(goalConflictTime)
		dayActivityDetailBob.responseData._links."yona:goal".href == goalBob.url

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Goal conflict of Richard with an 2k long URL is reported to Richard and Bob'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob
		ZonedDateTime now = YonaServer.now

		when:
		def url = buildLongUrl(2048)
		def response = analysisService.postToAnalysisEngine(richard, ["news/media"], url)

		then:
		response.status == 200
		def getMessagesRichardResponse = appService.getMessages(richard)
		getMessagesRichardResponse.status == 200
		def richardGoalConflictMessages = getMessagesRichardResponse.responseData._embedded."yona:messages".findAll
		{ it."@type" == "GoalConflictMessage" }
		richardGoalConflictMessages.size() == 1
		richardGoalConflictMessages[0].url == url

		assertMarkReadUnread(richard, richardGoalConflictMessages[0])

		def getMessagesBobResponse = appService.getMessages(bob)
		getMessagesBobResponse.status == 200
		def bobGoalConflictMessages = getMessagesBobResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		bobGoalConflictMessages.size() == 1
		bobGoalConflictMessages[0].url == null

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Goal conflict of Richard with a more than 2k long URL is trunated and reported to Richard and Bob'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob
		ZonedDateTime now = YonaServer.now

		when:
		def url = buildLongUrl(2049)
		def response = analysisService.postToAnalysisEngine(richard, ["news/media"], url)

		then:
		response.status == 200
		def getMessagesRichardResponse = appService.getMessages(richard)
		getMessagesRichardResponse.status == 200
		def richardGoalConflictMessages = getMessagesRichardResponse.responseData._embedded."yona:messages".findAll
		{ it."@type" == "GoalConflictMessage" }
		richardGoalConflictMessages.size() == 1
		richardGoalConflictMessages[0].url == url.substring(0, 2048)

		assertMarkReadUnread(richard, richardGoalConflictMessages[0])

		def getMessagesBobResponse = appService.getMessages(bob)
		getMessagesBobResponse.status == 200
		def bobGoalConflictMessages = getMessagesBobResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		bobGoalConflictMessages.size() == 1
		bobGoalConflictMessages[0].url == null

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	private def buildLongUrl(def length){
		def baseUrl = "http://www.abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijk.com/?queryString="
		def queryString = 'X'*(length - baseUrl.length())
		return baseUrl + queryString
	}

	def 'Richard removes Bob as buddy, so goal conflicts from Bob are gone'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob
		analysisService.postToAnalysisEngine(richard, ["news/media"], "http://www.refdag.nl")
		analysisService.postToAnalysisEngine(bob, ["Gambling"], "http://www.poker.com")
		def buddy = appService.getBuddies(richard)[0]

		when:
		sleep(100) // So we are sure the request time differs from the remove buddy time
		ZonedDateTime removeBuddyTime = YonaServer.now
		def response = appService.removeBuddy(richard, buddy, "Bob, as you know our ways parted, so I'll remove you as buddy.")
		richard = appService.reloadUser(richard)
		bob = appService.reloadUser(bob)

		then:
		response.status == 200
		richard.buddies.size() == 0 // Buddy removed for Richard
		bob.buddies.size() == 1 // Buddy not yet removed for Bob (not processed yet)
		AppService.assertDateTimeFormat(bob.buddies[0].lastStatusChangeTime)
		AppService.assertEquals(bob.buddies[0].lastStatusChangeTime, removeBuddyTime)

		def getMessagesRichardResponse = appService.getMessages(richard)
		getMessagesRichardResponse.status == 200
		getMessagesRichardResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "BuddyConnectResponseMessages"}.size() == 0
		def richardGoalConflictMessages = getMessagesRichardResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		richardGoalConflictMessages.size() == 1
		richardGoalConflictMessages[0].nickname == "RQ (me)"
		richardGoalConflictMessages[0]._links."yona:activityCategory".href == NEWS_ACT_CAT_URL

		def getMessagesBobResponse = appService.getMessages(bob)
		getMessagesBobResponse.status == 200
		getMessagesBobResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "BuddyConnectRequestMessages"}.size() == 0
		def bobGoalConflictMessages = getMessagesBobResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		bobGoalConflictMessages.size() == 1
		bobGoalConflictMessages[0].nickname == "BD (me)"
		bobGoalConflictMessages[0]._links."yona:activityCategory".href == GAMBLING_ACT_CAT_URL

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Bob receives the buddy removal of Richard'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob
		analysisService.postToAnalysisEngine(richard, ["news/media"], "http://www.refdag.nl")
		analysisService.postToAnalysisEngine(bob, ["Gambling"], "http://www.poker.com")
		def buddy = appService.getBuddies(richard)[0]
		def message = "Bob, as you know our ways parted, so I'll remove you as buddy."
		appService.removeBuddy(richard, buddy, message)

		when:
		def response = appService.getMessages(bob)

		then:
		response.status == 200
		def buddyDisconnectMessages = response.responseData._embedded."yona:messages".findAll{ it."@type" == "BuddyDisconnectMessage"}
		buddyDisconnectMessages.size() == 1
		buddyDisconnectMessages[0].reason == "USER_REMOVED_BUDDY"
		buddyDisconnectMessages[0].nickname == "${richard.nickname}"
		buddyDisconnectMessages[0]._embedded?."yona:user"?.firstName == "Richard"
		buddyDisconnectMessages[0]._links."yona:user" == null
		AppService.assertEquals(buddyDisconnectMessages[0].creationTime, YonaServer.now)
		buddyDisconnectMessages[0].message == message
		buddyDisconnectMessages[0]._links.self.href.startsWith(YonaServer.stripQueryString(bob.messagesUrl))
		buddyDisconnectMessages[0]._links."yona:process" == null // Processing happens automatically these days

		assertMarkReadUnread(bob, buddyDisconnectMessages[0])

		appService.getBuddies(bob).size() == 0

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Richard receives the buddy removal of Bob'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob
		analysisService.postToAnalysisEngine(richard, ["news/media"], "http://www.refdag.nl")
		analysisService.postToAnalysisEngine(bob, ["Gambling"], "http://www.poker.com")
		def buddy = appService.getBuddies(bob)[0]
		def message = "Richard, as you know our ways parted, so I'll remove you as buddy."
		sleep(100) // So we are sure the request time differs from the remove buddy time
		ZonedDateTime removeBuddyTime = YonaServer.now
		appService.removeBuddy(bob, buddy, message)

		when:
		def response = appService.getMessages(richard)
		richard = appService.reloadUser(richard)

		then:
		response.status == 200

		def buddyDisconnectMessages = response.responseData._embedded."yona:messages".findAll{ it."@type" == "BuddyDisconnectMessage"}
		buddyDisconnectMessages.size() == 1
		buddyDisconnectMessages[0].reason == "USER_REMOVED_BUDDY"
		buddyDisconnectMessages[0].nickname == "${bob.nickname}"
		buddyDisconnectMessages[0]._embedded?."yona:user"?.firstName == "Bob"
		buddyDisconnectMessages[0]._links."yona:user" == null
		AppService.assertEquals(buddyDisconnectMessages[0].creationTime, YonaServer.now)
		buddyDisconnectMessages[0].message == message
		buddyDisconnectMessages[0]._links.self.href.startsWith(YonaServer.stripQueryString(richard.messagesUrl))
		buddyDisconnectMessages[0]._links."yona:process" == null // Processing happens automatically these days

		richard.buddies.size() == 0 // Buddy not yet removed for Richard (not processed yet)

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'After Richard removed Bob as buddy, new goal conflicts are not reported to the buddies anymore'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob
		def buddy = appService.getBuddies(richard)[0]

		when:
		appService.removeBuddy(richard, buddy, "Bob, as you know our ways parted, so I'll remove you as buddy.")

		then:
		appService.getBuddies(richard).size() == 0 // Buddy removed for Richard
		appService.getBuddies(bob).size() == 1 // Buddy not yet removed for Bob (not processed yet)
		!appService.getBuddies(bob)[0].goals

		assertGoalConflictIsNotReportedToBuddy(richard, bob)
		assertGoalConflictIsNotReportedToBuddy(bob, richard)

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Richard deletes Bob\'s buddy entry before Bob accepts'()
	{
		given:
		User richard = addRichard()
		User bob = addBob()
		bob.emailAddress = "bob@dunn.net"
		appService.sendBuddyConnectRequest(richard, bob)

		when:
		def response = appService.removeBuddy(richard, appService.getBuddies(richard)[0], "Sorry, I regret having asked you")

		then:
		response.status == 200
		appService.getBuddies(bob).size() == 0 // Bob didn't accept Richard's request yet
		appService.getBuddies(richard).size() == 0 // Buddy removed for Richard

		// Connect request message is removed, so Bob doesn't have any messages
		appService.getMessages(bob).responseData.page.totalElements == 0

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Richard deletes Bob\'s buddy entry before processing Bob\'s accept message'()
	{
		given:
		User richard = addRichard()
		User bob = addBob()
		bob.emailAddress = "bob@dunn.net"
		appService.sendBuddyConnectRequest(richard, bob)
		def acceptUrl = appService.fetchBuddyConnectRequestMessage(bob).acceptUrl
		appService.postMessageActionWithPassword(acceptUrl, ["message" : "Yes, great idea!"], bob.password)

		when:
		def response = appService.removeBuddy(richard, appService.getBuddies(richard)[0], "Sorry, I regret having asked you")

		then:
		response.status == 200
		appService.getBuddies(bob).size() == 1 // Buddy not yet removed for Bob (didn't process the disconnect)
		appService.getBuddies(richard).size() == 0 // Buddy removed for Richard

		appService.getMessages(bob).responseData.page.totalElements == 1 // Only the disconnect message

		def disconnectMessage = appService.getMessages(bob).responseData._embedded."yona:messages".findAll{ it."@type" == "BuddyDisconnectMessage"}[0]
		disconnectMessage._links."yona:process" == null // Processing happens automatically these days

		appService.getBuddies(bob).size() == 0 // Buddy removed now

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Bob deletes Richard\'s buddy entry before Richard processed the accept message'()
	{
		given:
		User richard = addRichard()
		User bob = addBob()
		bob.emailAddress = "bob@dunn.net"
		appService.sendBuddyConnectRequest(richard, bob)
		def acceptUrl = appService.fetchBuddyConnectRequestMessage(bob).acceptUrl
		appService.postMessageActionWithPassword(acceptUrl, ["message" : "Yes, great idea!"], bob.password)

		when:
		def response = appService.removeBuddy(bob, appService.getBuddies(bob)[0], "Sorry, I regret accepting you")

		then:
		response.status == 200
		appService.getBuddies(bob).size() == 0 // Buddy removed for Bob
		def buddiesRichard = appService.getBuddies(richard)
		buddiesRichard.size() == 1 // Buddy not yet removed for Richard
		Buddy buddy = buddiesRichard[0]
		buddy.sendingStatus == "REQUESTED"
		buddy.receivingStatus == "REQUESTED"

		def messagesRichard = appService.getMessages(richard)
		messagesRichard.responseData.page.totalElements == 1
		messagesRichard.responseData._embedded."yona:messages".findAll{ it."@type" == "BuddyDisconnectMessage"}?.size() == 1
		def disconnectMessage = messagesRichard.responseData._embedded."yona:messages".findAll{ it."@type" == "BuddyDisconnectMessage"}[0]
		disconnectMessage._links."yona:process" == null // Processing happens automatically these days

		appService.getBuddies(richard).size() == 0 // Buddy now removed for Richard

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Richard and Bob connect and break up multiple times'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		richard.emailAddress = "richard@quinn.com"
		User bob = richardAndBob.bob
		bob.emailAddress = "bob@dunn.net"

		when:
		disconnectBuddy(richard, bob)

		then:
		makeBuddies(bob, richard)
		disconnectBuddy(bob, richard)
		makeBuddies(bob, richard)
		disconnectBuddy(richard, bob)
		makeBuddies(richard, bob)
		disconnectBuddy(richard, bob)
		makeBuddies(richard, bob)
		disconnectBuddy(bob, richard)
		makeBuddies(bob, richard)
		assertGoalConflictIsReportedToBuddy(bob, richard)
		assertGoalConflictIsReportedToBuddy(richard, bob)

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Richard sees Bob\'s "last seen" info when there were no activities'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob

		when:
		richard = appService.reloadUser(richard)
		Buddy buddyBob = richard.buddies[0]

		then:
		AppService.assertEquals(buddyBob.user.appLastOpenedDate, YonaServer.now.toLocalDate())
		buddyBob.lastMonitoredActivityDate == null

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Richard sees Bob\'s "last seen" info after one activity'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob

		when:
		def relativeActivityDate = "W-1 Thu 15:00"
		reportNetworkActivity(bob, ["YouTube"], "http://www.youtube.com", relativeActivityDate)
		richard = appService.reloadUser(richard)
		Buddy buddyBob = richard.buddies[0]

		then:
		AppService.assertEquals(buddyBob.user.appLastOpenedDate, YonaServer.now.toLocalDate())
		buddyBob.lastMonitoredActivityDate == YonaServer.relativeDateTimeStringToZonedDateTime(relativeActivityDate).toLocalDate()

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Richard sees Bob\'s "last seen" info after multiple activities'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob

		when:
		def relativeActivityDate = "W-1 Sat 00:10"
		reportNetworkActivity(bob, ["YouTube"], "http://www.youtube.com", "W-1 Thu 15:00")
		reportAppActivity(bob, "NU.nl", "W-1 Fri 23:55", relativeActivityDate)
		richard = appService.reloadUser(richard)
		Buddy buddyBob = richard.buddies[0]

		then:
		AppService.assertEquals(buddyBob.user.appLastOpenedDate, YonaServer.now.toLocalDate())
		buddyBob.lastMonitoredActivityDate == YonaServer.relativeDateTimeStringToZonedDateTime(relativeActivityDate).toLocalDate()

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	private void makeBuddies(User user, User buddy) {
		appService.makeBuddies(user, buddy)
		appService.getBuddies(user).size() == 1
		appService.getBuddies(buddy).size() == 1
	}

	private void disconnectBuddy(User user, User buddy)
	{
		def response = appService.removeBuddy(user, appService.getBuddies(user)[0], "Good luck")
		assert response.status == 200
		processBuddyDisconnectMessage(buddy)
		assert appService.getBuddies(user).size() == 0
		assert appService.getBuddies(buddy).size() == 0
	}

	private void processBuddyDisconnectMessage(User user)
	{
		def disconnectMessage = appService.getMessages(user).responseData._embedded."yona:messages".findAll{ it."@type" == "BuddyDisconnectMessage"}[0]
		disconnectMessage._links."yona:process" == null // Processing happens automatically these days
	}

	private void assertGoalConflictIsReportedToBuddy(User user, User buddy)
	{
		analysisService.postToAnalysisEngine(user, ["Gambling"], "http://www.poker.com")
		def responseGetMessagesBuddy = appService.getMessages(buddy)
		assert responseGetMessagesBuddy.status == 200
		def goalConflictMessagesBuddy = responseGetMessagesBuddy.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		assert goalConflictMessagesBuddy.size() == 1
		assert goalConflictMessagesBuddy[0].nickname == user.nickname
		assert goalConflictMessagesBuddy[0]._links."yona:activityCategory".href == GAMBLING_ACT_CAT_URL
		assert goalConflictMessagesBuddy[0].url == null

		def responseDeleteMessageBuddy = appService.deleteResourceWithPassword(goalConflictMessagesBuddy[0]._links.edit.href, buddy.password)
		responseDeleteMessageBuddy.status == 200

		def responseGetMessagesUser = appService.getMessages(user)
		assert responseGetMessagesUser.status == 200
		def goalConflictMessagesUser = responseGetMessagesUser.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		assert goalConflictMessagesUser.size() == 1
		def responseDeleteMessageUser = appService.deleteResourceWithPassword(goalConflictMessagesUser[0]._links.edit.href, user.password)
		responseDeleteMessageUser.status == 200
	}

	private void assertGoalConflictIsNotReportedToBuddy(User user, User buddy)
	{
		analysisService.postToAnalysisEngine(user, ["Gambling"], "http://www.poker.com")
		def responseGetMessagesBuddy = appService.getMessages(buddy)
		assert responseGetMessagesBuddy.status == 200
		assert responseGetMessagesBuddy.responseData._embedded?."yona:messages"?.find{ it."@type" == "GoalConflictMessage"} == null

		def responseGetMessagesUser = appService.getMessages(user)
		assert responseGetMessagesUser.status == 200
		def goalConflictMessagesUser = responseGetMessagesUser.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		assert goalConflictMessagesUser.size() == 1
		def responseDeleteMessageUser = appService.deleteResourceWithPassword(goalConflictMessagesUser[0]._links.edit.href, user.password)
		responseDeleteMessageUser.status == 200
	}
}
