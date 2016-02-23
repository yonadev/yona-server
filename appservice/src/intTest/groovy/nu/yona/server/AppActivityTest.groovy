/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import groovy.json.*

class AppActivityTest extends AbstractAppServiceIntegrationTest
{
	def 'Hacking attempt: Try to post app activity without password'()
	{
		given:
		def richard = addRichard()
		def startTime = new Date(System.currentTimeMillis() - (60 * 60 * 1000)).getTime()
		def endTime = new Date().getTime()

		when:
		def response = appService.createResourceWithPassword(richard.url + appService.APP_ACTIVITY_PATH_FRAGMENT, """[{
					"application":"Poker App",
					"startTime":"$startTime",
					"endTime":"$endTime"
				}]""", "Hack")

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
		def startTime = new Date(System.currentTimeMillis() - (60 * 60 * 1000)).getTime()
		def endTime = new Date().getTime()

		when:
		appService.postAppActivityToAnalysisEngine(richard, "Poker App", startTime, endTime)

		then:
		def getMessagesRichardResponse = appService.getMessages(richard)
		getMessagesRichardResponse.status == 200
		getMessagesRichardResponse.responseData._embedded.goalConflictMessages
		getMessagesRichardResponse.responseData._embedded.goalConflictMessages.size() == 1
		getMessagesRichardResponse.responseData._embedded.goalConflictMessages[0].nickname == "<self>"
		getMessagesRichardResponse.responseData._embedded.goalConflictMessages[0].activityCategoryName == "gambling"

		def getMessagesBobResponse = appService.getMessages(bob)
		getMessagesBobResponse.status == 200
		getMessagesBobResponse.responseData._embedded.goalConflictMessages.size() == 1
		getMessagesBobResponse.responseData._embedded.goalConflictMessages[0].nickname == richard.nickname
		getMessagesBobResponse.responseData._embedded.goalConflictMessages[0].activityCategoryName == "gambling"

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
		def startTime = new Date(System.currentTimeMillis() - (60 * 60 * 1000)).getTime()
		def endTime = new Date(System.currentTimeMillis() - (10 * 1000)).getTime()
		def startTime1 = new Date(System.currentTimeMillis() - (10 * 1000)).getTime()
		def endTime1 = new Date().getTime()

		when:
		appService.postAppActivityToAnalysisEngine(richard, "Poker App", startTime, endTime)
		analysisService.postToAnalysisEngine(richard, ["Gambling"], "http://www.poker.com")
		appService.postAppActivityToAnalysisEngine(richard, "Lotto App", startTime1, endTime1)

		then:
		def getMessagesRichardResponse = appService.getMessages(richard)
		getMessagesRichardResponse.status == 200
		getMessagesRichardResponse.responseData._embedded.goalConflictMessages
		getMessagesRichardResponse.responseData._embedded.goalConflictMessages.size() == 1

		def getMessagesBobResponse = appService.getMessages(bob)
		getMessagesBobResponse.status == 200
		getMessagesBobResponse.responseData._embedded.goalConflictMessages.size() == 1
	}

	def 'Send multiple app activities after offline period'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		def richard = richardAndBob.richard
		def bob = richardAndBob.bob
		def startTime = new Date(System.currentTimeMillis() - (60 * 60 * 1000)).getTime()
		def endTime = new Date(System.currentTimeMillis() - (10 * 1000)).getTime()
		def startTime1 = new Date(System.currentTimeMillis() - (10 * 1000)).getTime()
		def endTime1 = new Date().getTime()

		when:
		def response = appService.createResourceWithPassword(richard.url + appService.APP_ACTIVITY_PATH_FRAGMENT, """[{
					"application":"Poker App",
					"startTime":"$startTime",
					"endTime":"$endTime"
				},
				{
					"application":"Lotto App",
					"startTime":"$startTime1",
					"endTime":"$endTime1"
				}]""", richard.password)

		then:
		def getMessagesRichardResponse = appService.getMessages(richard)
		getMessagesRichardResponse.status == 200
		getMessagesRichardResponse.responseData._embedded.goalConflictMessages
		getMessagesRichardResponse.responseData._embedded.goalConflictMessages.size() == 1

		def getMessagesBobResponse = appService.getMessages(bob)
		getMessagesBobResponse.status == 200
		getMessagesBobResponse.responseData._embedded.goalConflictMessages.size() == 1
	}
}
