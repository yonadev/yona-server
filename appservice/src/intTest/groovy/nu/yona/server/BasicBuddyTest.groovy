/*******************************************************************************
 * Copyright (c) 2015, 2018 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import static nu.yona.server.test.CommonAssertions.*

import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier

import groovy.json.*
import nu.yona.server.test.AppService
import nu.yona.server.test.Buddy
import nu.yona.server.test.CommonAssertions
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
		assertResponseStatus(response, 400)
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

		User bobby = makeUserForBuddyRequest(bob, "bob@dunn.net", "Bobby", "Dun")

		when:
		ZonedDateTime buddyRequestTime = YonaServer.now
		def response = appService.sendBuddyConnectRequest(richard, bobby)

		then:
		assertResponseStatus(response, 201)
		response.responseData._embedded."yona:user".firstName == bobby.firstName
		response.responseData._embedded."yona:user".lastName == bobby.lastName
		response.responseData._links."yona:user" == null
		response.responseData._links.self.href.startsWith(YonaServer.stripQueryString(richard.url))

		User richardWithBuddy = appService.reloadUser(richard)
		richardWithBuddy.buddies != null
		richardWithBuddy.buddies.size() == 1
		richardWithBuddy.buddies[0].user.firstName == bobby.firstName
		richardWithBuddy.buddies[0].user.lastName == bobby.lastName
		// goals and devices should not be embedded before accept
		richardWithBuddy.buddies[0].goals == null
		richardWithBuddy.buddies[0].user.goals == null
		richardWithBuddy.buddies[0].user.devices == null
		richardWithBuddy.buddies[0].sendingStatus == "REQUESTED"
		richardWithBuddy.buddies[0].receivingStatus == "REQUESTED"
		assertDateTimeFormat(richardWithBuddy.buddies[0].lastStatusChangeTime)
		assertEquals(richardWithBuddy.buddies[0].lastStatusChangeTime, buddyRequestTime)

		appService.getUser(CommonAssertions.&assertUserGetResponseDetailsWithBuddyData, richardWithBuddy.buddies[0].user.url, richard.password)

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
		assertResponseStatusOk(response)
		response.responseData._embedded."yona:messages".size() == 1
		def buddyConnectRequestMessages = response.responseData._embedded."yona:messages".findAll
		{ it."@type" == "BuddyConnectRequestMessage" }
		buddyConnectRequestMessages.size() == 1
		buddyConnectRequestMessages[0].nickname == richard.nickname
		assertEquals(buddyConnectRequestMessages[0].creationTime, YonaServer.now)
		buddyConnectRequestMessages[0].status == "REQUESTED"
		buddyConnectRequestMessages[0]._embedded."yona:user".firstName == "Richard"
		buddyConnectRequestMessages[0]._links."yona:user" == null
		buddyConnectRequestMessages[0]._links.self.href.startsWith(YonaServer.stripQueryString(bob.messagesUrl))
		buddyConnectRequestMessages[0]._links."yona:accept".href.startsWith(buddyConnectRequestMessages[0]._links.self.href)

		appService.getUser(CommonAssertions.&assertUserGetResponseDetailsWithoutPrivateData, buddyConnectRequestMessages[0]._embedded."yona:user"._links.self.href)

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
		assertResponseStatusOk(response)
		response.responseData.properties.status == "done"
		response.responseData._embedded."yona:affectedMessages".size() == 1
		response.responseData._embedded."yona:affectedMessages"[0]._links.self.href == connectRequestMessage.selfUrl
		response.responseData._embedded."yona:affectedMessages"[0].status == "ACCEPTED"
		response.responseData._embedded."yona:affectedMessages"[0]._links."yona:accept" == null
		response.responseData._embedded."yona:affectedMessages"[0]._links."yona:reject" == null

		List<Buddy> buddies = appService.getBuddies(bob)
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

		appService.getUser(CommonAssertions.&assertUserGetResponseDetailsWithBuddyData, buddies[0].user.url, bob.password)

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Richard finds Bob\'s buddy connect response'()
	{
		given:
		User richard = addRichard()
		User bob = addBob()
		User bobby = makeUserForBuddyRequest(bob, "bob@dunn.net", "Bobby", "Dun")
		appService.sendBuddyConnectRequest(richard, bobby)
		def acceptUrl = appService.fetchBuddyConnectRequestMessage(bob).acceptUrl
		appService.postMessageActionWithPassword(acceptUrl, ["message" : "Yes, great idea!"], bob.password)

		when:
		sleep(100) // So we are sure the request time differs from the processing time
		ZonedDateTime buddyResponseProcessTime = YonaServer.now
		def response = appService.getMessages(richard)

		then:
		assertResponseStatusOk(response)
		def buddyConnectResponseMessages = response.responseData._embedded."yona:messages".findAll
		{ it."@type" == "BuddyConnectResponseMessage" }
		buddyConnectResponseMessages[0]._links."yona:user".href.startsWith(YonaServer.stripQueryString(bob.url))
		buddyConnectResponseMessages[0]._embedded?."yona:user" == null
		buddyConnectResponseMessages[0].nickname == bob.nickname
		assertEquals(buddyConnectResponseMessages[0].creationTime, YonaServer.now)
		buddyConnectResponseMessages[0].status == "ACCEPTED"
		buddyConnectResponseMessages[0]._links.self.href.startsWith(YonaServer.stripQueryString(richard.messagesUrl))
		buddyConnectResponseMessages[0]._links."yona:process" == null // Processing happens automatically these days

		appService.getUser(CommonAssertions.&assertUserGetResponseDetailsWithBuddyData, buddyConnectResponseMessages[0]._links."yona:user".href, richard.password)

		assertMarkReadUnread(richard, buddyConnectResponseMessages[0])

		def buddies = appService.getBuddies(richard)
		buddies.size() == 1
		buddies[0].user.firstName == bob.firstName
		buddies[0].user.lastName == bob.lastName
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
		assertDateTimeFormat(richardWithBuddy.buddies[0].lastStatusChangeTime)
		assertEquals(richardWithBuddy.buddies[0].lastStatusChangeTime, buddyResponseProcessTime)

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
		Buddy[] buddiesRichard = appService.getBuddies(richard)
		Buddy[] buddiesBob = appService.getBuddies(bob)

		then:
		buddiesRichard[0].goals.size() == 2 // YD-505
		buddiesRichard[0].user.goals.size() == 2
		buddiesRichard[0].user.devices.size() == 1
		buddiesBob[0].goals.size() == 2 // YD-505
		buddiesBob[0].user.goals.size() == 2
		buddiesBob[0].user.devices.size() == 1

		def bobGoalUrls = bob.goals.collect { YonaServer.stripQueryString(it.url) }
		def richardBuddyGoalUrls = buddiesRichard[0].goals.collect { YonaServer.stripQueryString(it.url) } // YD-505
		def richardBuddyUserGoalUrls = buddiesRichard[0].user.goals.collect { YonaServer.stripQueryString(it.url) }

		assert bobGoalUrls == richardBuddyGoalUrls // YD-505
		assert bobGoalUrls == richardBuddyUserGoalUrls

		buddiesRichard[0].goals.each // YD-505
		{
			assertResponseStatusOk(appService.yonaServer.getResource(it.url, ["Yona-Password": richard.password]))
		}

		buddiesRichard[0].user.goals.each
		{
			assertResponseStatusOk(appService.yonaServer.getResource(it.url, ["Yona-Password": richard.password]))
		}

		buddiesBob[0].goals.each // YD-505
		{
			assertResponseStatusOk(appService.yonaServer.getResource(it.url, ["Yona-Password": bob.password]))
		}

		buddiesBob[0].user.goals.each
		{
			assertResponseStatusOk(appService.yonaServer.getResource(it.url, ["Yona-Password": bob.password]))
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
		assertResponseStatusNoContent(response)
		def getMessagesRichardResponse = appService.getMessages(richard)
		assertResponseStatusOk(getMessagesRichardResponse)
		def richardGoalConflictMessages = getMessagesRichardResponse.responseData._embedded."yona:messages".findAll
		{ it."@type" == "GoalConflictMessage" }
		richardGoalConflictMessages.size() == 1
		richardGoalConflictMessages[0].nickname == "RQ (me)"
		assertEquals(richardGoalConflictMessages[0].creationTime, now)
		assertEquals(richardGoalConflictMessages[0].activityStartTime, now)
		assertEquals(richardGoalConflictMessages[0].activityEndTime, now.plus(Duration.ofMinutes(1))) // Minimum duration 1 minute
		richardGoalConflictMessages[0]._links."yona:activityCategory".href == NEWS_ACT_CAT_URL
		richardGoalConflictMessages[0]._links."yona:buddy"?.href == null
		richardGoalConflictMessages[0].url == "http://www.refdag.nl"

		assertMarkReadUnread(richard, richardGoalConflictMessages[0])

		def getMessagesBobResponse = appService.getMessages(bob)
		assertResponseStatusOk(getMessagesBobResponse)
		def bobGoalConflictMessages = getMessagesBobResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		bobGoalConflictMessages.size() == 1
		bobGoalConflictMessages[0].nickname == richard.nickname
		assertEquals(bobGoalConflictMessages[0].creationTime, now)
		assertEquals(bobGoalConflictMessages[0].activityStartTime, now)
		assertEquals(bobGoalConflictMessages[0].activityEndTime, now.plus(Duration.ofMinutes(1))) // Minimum duration 1 minute
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
		assertResponseStatusOk(appService.postMessageActionWithPassword(acceptUrl, ["message" : "Yes, great idea!"], bob.password))

		when:
		def response = analysisService.postToAnalysisEngine(bob, ["news/media"], "http://www.refdag.nl")

		then:
		assertResponseStatusNoContent(response)
		def getMessagesRichardResponse = appService.getMessages(richard)
		assertResponseStatusOk(getMessagesRichardResponse)
		def richardGoalConflictMessages = getMessagesRichardResponse.responseData._embedded."yona:messages".findAll
		{ it."@type" == "GoalConflictMessage" }
		richardGoalConflictMessages.size() == 1
		richardGoalConflictMessages[0].nickname == bob.nickname

		def getMessagesBobResponse = appService.getMessages(bob)
		assertResponseStatusOk(getMessagesBobResponse)
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
		assertResponseStatusOk(getMessagesRichardResponse)
		getMessagesRichardResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}.size() == 1

		def getMessagesBobResponse = appService.getMessages(bob)
		assertResponseStatusOk(getMessagesBobResponse)
		getMessagesBobResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}.size() == 1

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Goal conflict of Bob (on iOS and Android) is reported to Richard and Bob'(def operatingSystem)
	{
		given:
		User richard = addRichard(false)
		User bob = addBob(false, operatingSystem)
		bob.emailAddress = "bob@dunn.com"
		appService.makeBuddies(richard, bob)
		richard = appService.reloadUser(richard)
		bob = appService.reloadUser(bob, CommonAssertions.&assertUserGetResponseDetailsWithPrivateDataIgnoreDefaultDevice)

		Goal goalBob = bob.findActiveGoal(GAMBLING_ACT_CAT_URL)
		Goal goalBuddyBob = richard.buddies[0].findActiveGoal(GAMBLING_ACT_CAT_URL)
		ZonedDateTime goalConflictTime = YonaServer.now

		when:
		analysisService.postToAnalysisEngine(bob, ["Gambling"], "http://www.poker.com")

		then:
		def getMessagesRichardResponse = appService.getMessages(richard)
		assertResponseStatusOk(getMessagesRichardResponse)
		def richardGoalConflictMessages = getMessagesRichardResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		richardGoalConflictMessages.size() == 1
		richardGoalConflictMessages[0].nickname == bob.nickname
		richardGoalConflictMessages[0]._links."yona:activityCategory".href == GAMBLING_ACT_CAT_URL
		richardGoalConflictMessages[0].url == null
		// link to Bob's activity present
		def dayActivityDetailUrlRichard = richardGoalConflictMessages[0]._links."yona:dayDetails".href
		dayActivityDetailUrlRichard
		def dayActivityDetailRichard = appService.getResourceWithPassword(dayActivityDetailUrlRichard, richard.password)
		assertResponseStatusOk(dayActivityDetailRichard)
		dayActivityDetailRichard.responseData.date == YonaServer.toIsoDateString(goalConflictTime)
		dayActivityDetailRichard.responseData._links."yona:goal".href == goalBuddyBob.url
		dayActivityDetailRichard.responseData.totalMinutesBeyondGoal == 1

		def getMessagesBobResponse = appService.getMessages(bob)
		assertResponseStatusOk(getMessagesBobResponse)
		def bobGoalConflictMessages = getMessagesBobResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		bobGoalConflictMessages.size() == 1
		bobGoalConflictMessages[0].nickname == "BD (me)"
		bobGoalConflictMessages[0]._links."yona:activityCategory".href == GAMBLING_ACT_CAT_URL
		bobGoalConflictMessages[0].url == "http://www.poker.com"
		// link to own activity present
		def dayActivityDetailUrlBob = bobGoalConflictMessages[0]._links."yona:dayDetails".href
		dayActivityDetailUrlBob
		def dayActivityDetailBob = appService.getResourceWithPassword(dayActivityDetailUrlBob, bob.password)
		assertResponseStatusOk(dayActivityDetailBob)
		dayActivityDetailBob.responseData.date == YonaServer.toIsoDateString(goalConflictTime)
		dayActivityDetailBob.responseData._links."yona:goal".href == goalBob.url
		dayActivityDetailBob.responseData.totalMinutesBeyondGoal == 1

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)

		where:
		operatingSystem | _
		"ANDROID" | _
		"IOS" | _
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
		assertResponseStatusNoContent(response)
		def getMessagesRichardResponse = appService.getMessages(richard)
		assertResponseStatusOk(getMessagesRichardResponse)
		def richardGoalConflictMessages = getMessagesRichardResponse.responseData._embedded."yona:messages".findAll
		{ it."@type" == "GoalConflictMessage" }
		richardGoalConflictMessages.size() == 1
		richardGoalConflictMessages[0].url == url

		assertMarkReadUnread(richard, richardGoalConflictMessages[0])

		def getMessagesBobResponse = appService.getMessages(bob)
		assertResponseStatusOk(getMessagesBobResponse)
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
		assertResponseStatusNoContent(response)
		def getMessagesRichardResponse = appService.getMessages(richard)
		assertResponseStatusOk(getMessagesRichardResponse)
		def richardGoalConflictMessages = getMessagesRichardResponse.responseData._embedded."yona:messages".findAll
		{ it."@type" == "GoalConflictMessage" }
		richardGoalConflictMessages.size() == 1
		richardGoalConflictMessages[0].url == url.substring(0, 2048)

		assertMarkReadUnread(richard, richardGoalConflictMessages[0])

		def getMessagesBobResponse = appService.getMessages(bob)
		assertResponseStatusOk(getMessagesBobResponse)
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
		assertResponseStatusOk(response)
		richard.buddies.size() == 0 // Buddy removed for Richard
		bob.buddies.size() == 0 // Buddy removed for Bob (processed as part of reload)

		def getMessagesRichardResponse = appService.getMessages(richard)
		assertResponseStatusOk(getMessagesRichardResponse)
		getMessagesRichardResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "BuddyConnectResponseMessages"}.size() == 0
		def richardGoalConflictMessages = getMessagesRichardResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		richardGoalConflictMessages.size() == 1
		richardGoalConflictMessages[0].nickname == "RQ (me)"
		richardGoalConflictMessages[0]._links."yona:activityCategory".href == NEWS_ACT_CAT_URL

		def getMessagesBobResponse = appService.getMessages(bob)
		assertResponseStatusOk(getMessagesBobResponse)
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
		assertResponseStatusOk(response)
		def buddyDisconnectMessages = response.responseData._embedded."yona:messages".findAll{ it."@type" == "BuddyDisconnectMessage"}
		buddyDisconnectMessages.size() == 1
		buddyDisconnectMessages[0].reason == "USER_REMOVED_BUDDY"
		buddyDisconnectMessages[0].nickname == "${richard.nickname}"
		buddyDisconnectMessages[0]._embedded?."yona:user"?.firstName == "Richard"
		buddyDisconnectMessages[0]._links."yona:user" == null
		assertEquals(buddyDisconnectMessages[0].creationTime, YonaServer.now)
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
		assertResponseStatusOk(response)

		def buddyDisconnectMessages = response.responseData._embedded."yona:messages".findAll{ it."@type" == "BuddyDisconnectMessage"}
		buddyDisconnectMessages.size() == 1
		buddyDisconnectMessages[0].reason == "USER_REMOVED_BUDDY"
		buddyDisconnectMessages[0].nickname == "${bob.nickname}"
		buddyDisconnectMessages[0]._embedded?."yona:user"?.firstName == "Bob"
		buddyDisconnectMessages[0]._links."yona:user" == null
		assertEquals(buddyDisconnectMessages[0].creationTime, YonaServer.now)
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
		appService.getBuddies(bob).size() == 0 // Buddy removed for Bob (processed during request handling)

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
		assertResponseStatusOk(response)
		appService.getBuddies(bob).size() == 0 // Bob didn't accept Richard's request yet
		appService.getBuddies(richard).size() == 0 // Buddy removed for Richard

		// Connect request message is removed, so Bob doesn't have any messages
		appService.getMessages(bob).responseData.page.totalElements == 0

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
		assertEquals(buddyBob.user.appLastOpenedDate, YonaServer.now.toLocalDate())
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
		assertEquals(buddyBob.user.appLastOpenedDate, YonaServer.now.toLocalDate())
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
		assertEquals(buddyBob.user.appLastOpenedDate, YonaServer.now.toLocalDate())
		buddyBob.lastMonitoredActivityDate == YonaServer.relativeDateTimeStringToZonedDateTime(relativeActivityDate).toLocalDate()

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Richard finds Bob\'s buddy connect response with multiple threads'()
	{
		given:
		User richard = addRichard()
		User bob = addBob()
		User bobby = makeUserForBuddyRequest(bob, "bob@dunn.net", "Bobby", "Dun")
		appService.sendBuddyConnectRequest(richard, bobby)
		def acceptUrl = appService.fetchBuddyConnectRequestMessage(bob).acceptUrl
		appService.postMessageActionWithPassword(acceptUrl, ["message" : "Yes, great idea!"], bob.password)

		int numThreads = 10
		CyclicBarrier startBarrier = new CyclicBarrier(numThreads)
		CountDownLatch doneSignal = new CountDownLatch(numThreads)
		List<MessagesRetriever> messagesRetrievers = [].withDefault { new MessagesRetriever(startBarrier, doneSignal, richard, bob)}
		numThreads.times{ new Thread(messagesRetrievers[it]).start()}

		when:
		doneSignal.await()

		then:
		messagesRetrievers.each{ it.assertSuccess()}

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
		assertResponseStatusOk(response)
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
		assertResponseStatusOk(responseGetMessagesBuddy)
		def goalConflictMessagesBuddy = responseGetMessagesBuddy.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		assert goalConflictMessagesBuddy.size() == 1
		assert goalConflictMessagesBuddy[0].nickname == user.nickname
		assert goalConflictMessagesBuddy[0]._links."yona:activityCategory".href == GAMBLING_ACT_CAT_URL
		assert goalConflictMessagesBuddy[0].url == null

		def responseDeleteMessageBuddy = appService.deleteResourceWithPassword(goalConflictMessagesBuddy[0]._links.edit.href, buddy.password)
		assertResponseStatusOk(responseDeleteMessageBuddy)

		def responseGetMessagesUser = appService.getMessages(user)
		assertResponseStatusOk(responseGetMessagesUser)
		def goalConflictMessagesUser = responseGetMessagesUser.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		assert goalConflictMessagesUser.size() == 1
		def responseDeleteMessageUser = appService.deleteResourceWithPassword(goalConflictMessagesUser[0]._links.edit.href, user.password)
		assertResponseStatusOk(responseDeleteMessageUser)
	}

	private void assertGoalConflictIsNotReportedToBuddy(User user, User buddy)
	{
		analysisService.postToAnalysisEngine(user, ["Gambling"], "http://www.poker.com")
		def responseGetMessagesBuddy = appService.getMessages(buddy)
		assertResponseStatusOk(responseGetMessagesBuddy)
		assert responseGetMessagesBuddy.responseData._embedded?."yona:messages"?.find{ it."@type" == "GoalConflictMessage"} == null

		def responseGetMessagesUser = appService.getMessages(user)
		assertResponseStatusOk(responseGetMessagesUser)
		def goalConflictMessagesUser = responseGetMessagesUser.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		assert goalConflictMessagesUser.size() == 1
		def responseDeleteMessageUser = appService.deleteResourceWithPassword(goalConflictMessagesUser[0]._links.edit.href, user.password)
		assertResponseStatusOk(responseDeleteMessageUser)
	}

	private class MessagesRetriever implements Runnable{
		private final CyclicBarrier startBarrier
		private final CountDownLatch doneSignal
		private User richard
		private User bob
		private def response
		private def AppService ownAppService = new AppService()

		MessagesRetriever(CyclicBarrier startBarrier, CountDownLatch doneSignal, User richard, User bob) {
			this.startBarrier = startBarrier
			this.doneSignal = doneSignal
			this.richard = richard
			this.bob = bob
		}
		public void run() {
			try {
				startBarrier.await()
				doWork()
				doneSignal.countDown()
			} catch (InterruptedException ex) {} // return;
		}

		void doWork() {
			response = ownAppService.getMessages(richard)
		}

		void assertSuccess()
		{
			assertResponseStatusOk(response)
			def buddyConnectResponseMessages = response.responseData._embedded."yona:messages".findAll
			{ it."@type" == "BuddyConnectResponseMessage" }
			buddyConnectResponseMessages[0]._links."yona:user".href.startsWith(YonaServer.stripQueryString(bob.url))
			buddyConnectResponseMessages[0]._embedded?."yona:user" == null
			buddyConnectResponseMessages[0].nickname == bob.nickname
			assertEquals(buddyConnectResponseMessages[0].creationTime, YonaServer.now)
			buddyConnectResponseMessages[0].status == "ACCEPTED"
			buddyConnectResponseMessages[0]._links.self.href.startsWith(YonaServer.stripQueryString(richard.messagesUrl))
			buddyConnectResponseMessages[0]._links."yona:process" == null // Processing happens automatically these days
		}
	}
}
