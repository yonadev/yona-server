/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import groovy.json.*

import java.time.Duration
import java.time.ZonedDateTime

import nu.yona.server.test.AppActivity
import spock.lang.IgnoreRest

class AppActivityTest extends AbstractAppServiceIntegrationTest
{
	def 'Hacking attempt: Try to post app activity without password'()
	{
		given:
		def richard = addRichard()
		ZonedDateTime testStartTime = YonaServer.now
		ZonedDateTime startTime = testStartTime.minus(Duration.ofHours(1))
		ZonedDateTime endTime = testStartTime
		def nowString = YonaServer.toIsoDateString(testStartTime)
		def startTimeString = YonaServer.toIsoDateString(startTime)
		def endTimeString = YonaServer.toIsoDateString(endTime)

		when:
		def response = appService.createResourceWithPassword(richard.appActivityUrl, """{
				"deviceDateTime" : "$nowString",
				"activities" : [{
					"application":"Poker App",
					"startTime":"$startTimeString",
					"endTime":"$endTimeString"
				}]}""", "Hack")

		then:
		response.status == 400
		response.responseData.code == "error.decrypting.data"
	}

	def 'Goal conflict of Richard is reported to Richard and Bob'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		def richard = richardAndBob.richard
		def bob = richardAndBob.bob
		ZonedDateTime testStartTime = YonaServer.now
		ZonedDateTime startTime = testStartTime
		ZonedDateTime endTime = testStartTime.plusHours(1)

		when:
		def response = appService.postAppActivityToAnalysisEngine(richard, AppActivity.singleActivity("Poker App", startTime, endTime))

		then:
		response.status == 200
		def getMessagesRichardResponse = appService.getMessages(richard)
		getMessagesRichardResponse.status == 200
		ZonedDateTime goalConflictTime = YonaServer.now
		def goalConflictMessagesRichard = getMessagesRichardResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		goalConflictMessagesRichard.size() == 1
		goalConflictMessagesRichard[0].nickname == "<self>"
		assertEquals(goalConflictMessagesRichard[0].creationTime, goalConflictTime)
		goalConflictMessagesRichard[0]._links."yona:activityCategory".href == GAMBLING_ACT_CAT_URL

		def getMessagesBobResponse = appService.getMessages(bob)
		getMessagesBobResponse.status == 200
		def goalConflictMessagesBob = getMessagesBobResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		goalConflictMessagesBob.size() == 1
		goalConflictMessagesBob[0].nickname == richard.nickname
		assertEquals(goalConflictMessagesBob[0].creationTime, goalConflictTime)
		assertEquals(goalConflictMessagesBob[0].activityStartTime, startTime)
		assertEquals(goalConflictMessagesBob[0].activityEndTime, endTime)
		goalConflictMessagesBob[0]._links."yona:activityCategory".href == GAMBLING_ACT_CAT_URL

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Device time difference is properly resolved'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		def richard = richardAndBob.richard
		def bob = richardAndBob.bob
		Duration offset = Duration.ofMinutes(45) // Off by 45 minutes
		ZonedDateTime testStartTime = YonaServer.now
		ZonedDateTime startTime = testStartTime
		ZonedDateTime endTime = testStartTime.plusHours(1)

		when:
		def testStartTimeWrong = testStartTime.plus(offset)
		def startTimeWrong = startTime.plus(offset)
		def endTimeWrong = endTime.plus(offset)
		def response = appService.postAppActivityToAnalysisEngine(richard, AppActivity.singleActivity(testStartTimeWrong, "Poker App", startTimeWrong, endTimeWrong))

		then:
		response.status == 200
		def getMessagesRichardResponse = appService.getMessages(richard)
		getMessagesRichardResponse.status == 200
		ZonedDateTime goalConflictTime = YonaServer.now
		def goalConflictMessagesRichard = getMessagesRichardResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		goalConflictMessagesRichard.size() == 1
		goalConflictMessagesRichard[0].nickname == "<self>"
		assertEquals(goalConflictMessagesRichard[0].creationTime, goalConflictTime)
		goalConflictMessagesRichard[0]._links."yona:activityCategory".href == GAMBLING_ACT_CAT_URL

		def getMessagesBobResponse = appService.getMessages(bob)
		getMessagesBobResponse.status == 200
		def goalConflictMessagesBob = getMessagesBobResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		goalConflictMessagesBob.size() == 1
		goalConflictMessagesBob[0].nickname == richard.nickname
		assertEquals(goalConflictMessagesBob[0].creationTime, goalConflictTime)
		assertEquals(goalConflictMessagesBob[0].activityStartTime, startTime)
		assertEquals(goalConflictMessagesBob[0].activityEndTime, endTime)
		goalConflictMessagesBob[0]._links."yona:activityCategory".href == GAMBLING_ACT_CAT_URL

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Two conflicts within the conflict interval are reported as one message for each person'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		def richard = richardAndBob.richard
		def bob = richardAndBob.bob
		ZonedDateTime testStartTime = YonaServer.now
		ZonedDateTime startTime = testStartTime
		ZonedDateTime endTime = testStartTime.plusHours(1)
		ZonedDateTime startTime1 = testStartTime
		ZonedDateTime endTime1 = testStartTime.plusSeconds(10)

		when:
		appService.postAppActivityToAnalysisEngine(richard, AppActivity.singleActivity("Poker App", startTime, endTime))
		analysisService.postToAnalysisEngine(richard, ["Gambling"], "http://www.poker.com")
		appService.postAppActivityToAnalysisEngine(richard, AppActivity.singleActivity("Lotto App", startTime1, endTime1))

		then:
		def getMessagesRichardResponse = appService.getMessages(richard)
		getMessagesRichardResponse.status == 200
		def goalConflictMessagesRichard = getMessagesRichardResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		goalConflictMessagesRichard.size() == 1

		def getMessagesBobResponse = appService.getMessages(bob)
		getMessagesBobResponse.status == 200
		def goalConflictMessagesBob = getMessagesBobResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		goalConflictMessagesBob.size() == 1
	}

	def 'Send multiple app activities after offline period'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		def richard = richardAndBob.richard
		def bob = richardAndBob.bob
		ZonedDateTime testStartTime = YonaServer.now
		ZonedDateTime startTime = testStartTime
		ZonedDateTime endTime = testStartTime.plusHours(1)
		ZonedDateTime startTime1 = endTime
		ZonedDateTime endTime1 = endTime.plusSeconds(10)

		when:
		def response = appService.postAppActivityToAnalysisEngine(richard,
				new AppActivity([new AppActivity.Activity("Poker App", startTime, endTime), new AppActivity.Activity("Lotto App", , startTime1, endTime1)].toArray()))

		then:
		response.status == 200
		def getMessagesRichardResponse = appService.getMessages(richard)
		getMessagesRichardResponse.status == 200
		ZonedDateTime goalConflictTime = YonaServer.now
		def goalConflictMessagesRichard = getMessagesRichardResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		goalConflictMessagesRichard.size() == 1
		assertEquals(goalConflictMessagesRichard[0].creationTime, goalConflictTime)
		assertEquals(goalConflictMessagesRichard[0].activityStartTime, startTime)
		assertEquals(goalConflictMessagesRichard[0].activityEndTime, endTime1)

		def getMessagesBobResponse = appService.getMessages(bob)
		getMessagesBobResponse.status == 200
		def goalConflictMessagesBob = getMessagesBobResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		goalConflictMessagesBob.size() == 1
		assertEquals(goalConflictMessagesRichard[0].creationTime, goalConflictTime)
		assertEquals(goalConflictMessagesRichard[0].activityStartTime, startTime)
		assertEquals(goalConflictMessagesRichard[0].activityEndTime, endTime1)
	}

	def 'Activity before goal creation is ignored'()
	{
		given:
		def richard = addRichard()
		ZonedDateTime testStartTime = YonaServer.now
		ZonedDateTime startTime = testStartTime.minusDays(1)
		ZonedDateTime endTime = startTime.plusMinutes(15)

		when:
		def response = appService.postAppActivityToAnalysisEngine(richard,
				new AppActivity([new AppActivity.Activity("Poker App", startTime, endTime)].toArray()))

		then:
		response.status == 200
		def getMessagesRichardResponse = appService.getMessages(richard)
		getMessagesRichardResponse.status == 200
		ZonedDateTime goalConflictTime = YonaServer.now
		getMessagesRichardResponse.responseData._embedded?."yona:messages"?.findAll{ it."@type" == "GoalConflictMessage"} == null
	}
}
