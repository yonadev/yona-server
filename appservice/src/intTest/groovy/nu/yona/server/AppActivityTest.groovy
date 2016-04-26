/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import groovy.json.*
import nu.yona.server.test.AppActivity

class AppActivityTest extends AbstractAppServiceIntegrationTest
{
	def 'Hacking attempt: Try to post app activity without password'()
	{
		given:
		def richard = addRichard()
		def now = new Date()
		def startTime = new Date(System.currentTimeMillis() - (60 * 60 * 1000))
		def endTime = now
		def nowString = YonaServer.toIsoDateString(now)
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
		def startTime = new Date(System.currentTimeMillis() - (60 * 60 * 1000))
		def endTime = new Date()

		when:
		def response = appService.postAppActivityToAnalysisEngine(richard, AppActivity.singleActivity("Poker App", startTime, endTime))

		then:
		response.status == 200
		def getMessagesRichardResponse = appService.getMessages(richard)
		getMessagesRichardResponse.status == 200
		def goalConflictMessagesRichard = getMessagesRichardResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		goalConflictMessagesRichard.size() == 1
		goalConflictMessagesRichard[0].nickname == "<self>"
		assertEquals(goalConflictMessagesRichard[0].creationTime, new Date())
		goalConflictMessagesRichard[0]._links."yona:activityCategory".href == GAMBLING_ACT_CAT_URL

		def getMessagesBobResponse = appService.getMessages(bob)
		getMessagesBobResponse.status == 200
		def goalConflictMessagesBob = getMessagesBobResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		goalConflictMessagesBob.size() == 1
		goalConflictMessagesBob[0].nickname == richard.nickname
		assertEquals(goalConflictMessagesBob[0].creationTime, new Date())
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
		long nowMillis = System.currentTimeMillis()
		long offsetMillis = 45 * 60 * 1000 // Off by 45 minutes
		long startTimeMillis = nowMillis - (60 * 60 * 1000)
		long endTimeMillis = nowMillis

		when:
		def nowWrong = new Date(nowMillis + offsetMillis)
		def startTimeWrong = new Date(startTimeMillis + offsetMillis)
		def endTimeWrong = new Date(endTimeMillis + offsetMillis)
		def response = appService.postAppActivityToAnalysisEngine(richard, AppActivity.singleActivity(nowWrong, "Poker App", startTimeWrong, endTimeWrong))

		then:
		response.status == 200
		def getMessagesRichardResponse = appService.getMessages(richard)
		getMessagesRichardResponse.status == 200
		def goalConflictMessagesRichard = getMessagesRichardResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		goalConflictMessagesRichard.size() == 1
		goalConflictMessagesRichard[0].nickname == "<self>"
		assertEquals(goalConflictMessagesRichard[0].creationTime, new Date())
		goalConflictMessagesRichard[0]._links."yona:activityCategory".href == GAMBLING_ACT_CAT_URL

		def getMessagesBobResponse = appService.getMessages(bob)
		getMessagesBobResponse.status == 200
		def goalConflictMessagesBob = getMessagesBobResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		goalConflictMessagesBob.size() == 1
		goalConflictMessagesBob[0].nickname == richard.nickname
		assertEquals(goalConflictMessagesBob[0].creationTime, new Date(nowMillis))
		assertEquals(goalConflictMessagesBob[0].activityStartTime, new Date(startTimeMillis))
		assertEquals(goalConflictMessagesBob[0].activityEndTime, new Date(endTimeMillis))
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
		def startTime = new Date(System.currentTimeMillis() - (60 * 60 * 1000))
		def endTime = new Date(System.currentTimeMillis() - (10 * 1000))
		def startTime1 = new Date(System.currentTimeMillis() - (10 * 1000))
		def endTime1 = new Date()

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
		def startTime = new Date(System.currentTimeMillis() - (60 * 60 * 1000))
		def endTime = new Date(System.currentTimeMillis() - (10 * 1000))
		def startTime1 = new Date(System.currentTimeMillis() - (10 * 1000))
		def endTime1 = new Date()

		when:
		def response = appService.postAppActivityToAnalysisEngine(richard,
				new AppActivity([new AppActivity.Activity("Poker App", startTime, endTime), new AppActivity.Activity("Lotto App", , startTime1, endTime1)].toArray()))

		then:
		response.status == 200
		def getMessagesRichardResponse = appService.getMessages(richard)
		getMessagesRichardResponse.status == 200
		def goalConflictMessagesRichard = getMessagesRichardResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		goalConflictMessagesRichard.size() == 1
		assertEquals(goalConflictMessagesRichard[0].creationTime, new Date())
		assertEquals(goalConflictMessagesRichard[0].activityStartTime, startTime)
		assertEquals(goalConflictMessagesRichard[0].activityEndTime, endTime1)

		def getMessagesBobResponse = appService.getMessages(bob)
		getMessagesBobResponse.status == 200
		def goalConflictMessagesBob = getMessagesBobResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		goalConflictMessagesBob.size() == 1
		assertEquals(goalConflictMessagesRichard[0].creationTime, new Date())
		assertEquals(goalConflictMessagesRichard[0].activityStartTime, startTime)
		assertEquals(goalConflictMessagesRichard[0].activityEndTime, endTime1)
	}
}
