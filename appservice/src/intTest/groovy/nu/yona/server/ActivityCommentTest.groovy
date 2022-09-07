/*******************************************************************************
 * Copyright (c) 2017, 2022 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import static nu.yona.server.test.CommonAssertions.assertResponseStatus
import static nu.yona.server.test.CommonAssertions.assertResponseStatusNoContent
import static nu.yona.server.test.CommonAssertions.assertResponseStatusOk

import org.apache.commons.lang3.StringUtils

import nu.yona.server.test.AppService
import nu.yona.server.test.Buddy
import nu.yona.server.test.Goal
import nu.yona.server.test.User

class ActivityCommentTest extends AbstractAppServiceIntegrationTest
{
	final static def BASE_LINKS = ["self", "edit", "yona:user", "yona:markRead", "yona:threadHead"] as Set

	def 'Comment on buddy day activity'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob

		when:
		setCreationTime(richard, "W-1 Mon 02:18")
		setCreationTime(bob, "W-1 Mon 02:18")
		setGoalCreationTime(bob, NEWS_ACT_CAT_URL, "W-1 Mon 02:18")
		reportAppActivity(bob, bob.requestingDevice, "NU.nl", "W-1 Tue 03:15", "W-1 Tue 03:35")

		richard = appService.reloadUser(richard)
		bob = appService.reloadUser(bob)

		then:
		assertCommentingWorks(richard, bob, false, { user -> appService.getDayActivityOverviews(user, ["size": 14]) },
				{ User user -> appService.getDayActivityOverviews(user, user.buddies[0], ["size": 14]) },
				{ responseOverviews, user, goal -> appService.getDayDetailsFromOverview(responseOverviews, user, goal, 1, "Tue") })

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Comment on buddy week activity'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob

		when:
		setCreationTime(richard, "W-1 Mon 02:18")
		setCreationTime(bob, "W-1 Mon 02:18")
		setGoalCreationTime(bob, NEWS_ACT_CAT_URL, "W-1 Mon 02:18")
		reportAppActivity(bob, bob.requestingDevice, "NU.nl", "W-1 Tue 03:15", "W-1 Tue 03:35")

		richard = appService.reloadUser(richard)
		bob = appService.reloadUser(bob)

		then:
		assertCommentingWorks(richard, bob, true, { user -> appService.getWeekActivityOverviews(user, ["size": 14]) },
				{ User user -> appService.getWeekActivityOverviews(user, user.buddies[0], ["size": 14]) },
				{ responseOverviews, user, goal -> appService.getWeekDetailsFromOverview(responseOverviews, user, goal, 1) })

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Comments of buddies of buddy are not visible'()
	{
		given:
		def richardBobAndBea = addRichardWithBobAndBeaAsBuddies()
		User richard = richardBobAndBea.richard
		User bob = richardBobAndBea.bob
		User bea = richardBobAndBea.bea
		setCreationTime(richard, "W-1 Mon 02:18")
		setCreationTime(bob, "W-1 Mon 02:18")
		setCreationTime(bea, "W-1 Mon 02:18")
		setGoalCreationTime(richard, NEWS_ACT_CAT_URL, "W-1 Mon 02:18")
		richard = appService.reloadUser(richard)
		bob = appService.reloadUser(bob)
		bea = appService.reloadUser(bea)
		Goal richardGoal = richard.findActiveGoal(NEWS_ACT_CAT_URL)
		Goal bobGoalBuddyRichard = bob.buddies[0].findActiveGoal(NEWS_ACT_CAT_URL)
		Goal beaGoalBuddyRichard = bea.buddies[0].findActiveGoal(NEWS_ACT_CAT_URL)
		def bobResponseDetailsRichardAsBuddy = appService.getDayActivityDetails(bob, bob.buddies[0], bobGoalBuddyRichard, 1, "Tue")
		def beaResponseDetailsRichardAsBuddy = appService.getDayActivityDetails(bea, bea.buddies[0], beaGoalBuddyRichard, 1, "Tue")
		def richardResponseDetails = appService.getDayActivityDetails(richard, richardGoal, 1, "Tue")

		when:
		addComment(bobResponseDetailsRichardAsBuddy, """{"message": "Hi buddy! How ya doing?"}""", bob.password)
		addComment(beaResponseDetailsRichardAsBuddy, """{"message": "Hi Richard! Everything alright?"}""", bea.password)

		def expectedDataRichard = [[nickname: "BD", message: "Hi buddy! How ya doing?"], [nickname: "BDD", message: "Hi Richard! Everything alright?"]]
		List richardMessages = getActivityDetailMessages(richardResponseDetails, richard, expectedDataRichard).responseData._embedded."yona:messages"
		def messageBob1AsSeenByRichard = richardMessages[0]
		assert messageBob1AsSeenByRichard.nickname == "BD"
		def messageBea1AsSeenByRichard = richardMessages[1]
		assert messageBea1AsSeenByRichard.nickname == "BDD"
		appService.postMessageActionWithPassword(messageBob1AsSeenByRichard._links."yona:reply".href, ["message": "Hi Bob! Doing fine!"], richard.password)

		def expectedDataBob = [[nickname: "BD", message: "Hi buddy! How ya doing?"], [nickname: "RQ", message: "Hi Bob! Doing fine!"]]
		List bobMessagesRichard = getActivityDetailMessages(bobResponseDetailsRichardAsBuddy, bob, expectedDataBob).responseData._embedded."yona:messages"
		def expectedDataBea = [[nickname: "BDD", message: "Hi Richard! Everything alright?"]]
		List beaMessagesRichard = getActivityDetailMessages(beaResponseDetailsRichardAsBuddy, bea, expectedDataBea).responseData._embedded."yona:messages"

		then:
		bobMessagesRichard[0].nickname == "BD (me)"
		bobMessagesRichard[1].nickname == "RQ"

		beaMessagesRichard[0].nickname == "BDD (me)"

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
		appService.deleteUser(bea)
	}

	def 'Comments are returned in thread order'()
	{
		given:
		def richardBobAndBea = addRichardWithBobAndBeaAsBuddies()
		User richard = richardBobAndBea.richard
		User bob = richardBobAndBea.bob
		User bea = richardBobAndBea.bea
		setCreationTime(richard, "W-1 Mon 02:18")
		setCreationTime(bob, "W-1 Mon 02:18")
		setCreationTime(bea, "W-1 Mon 02:18")
		setGoalCreationTime(richard, NEWS_ACT_CAT_URL, "W-1 Mon 02:18")
		richard = appService.reloadUser(richard)
		bob = appService.reloadUser(bob)
		bea = appService.reloadUser(bea)
		Goal richardGoal = richard.findActiveGoal(NEWS_ACT_CAT_URL)
		Goal bobGoalBuddyRichard = bob.buddies[0].findActiveGoal(NEWS_ACT_CAT_URL)
		Goal beaGoalBuddyRichard = bea.buddies[0].findActiveGoal(NEWS_ACT_CAT_URL)
		def bobResponseDetailsRichardAsBuddy = appService.getDayActivityDetails(bob, bob.buddies[0], bobGoalBuddyRichard, 1, "Tue")
		def beaResponseDetailsRichardAsBuddy = appService.getDayActivityDetails(bea, bea.buddies[0], beaGoalBuddyRichard, 1, "Tue")
		def richardResponseDetails = appService.getDayActivityDetails(richard, richardGoal, 1, "Tue")

		when:
		addComment(bobResponseDetailsRichardAsBuddy, """{"message": "Hi buddy! How ya doing?"}""", bob.password)
		addComment(beaResponseDetailsRichardAsBuddy, """{"message": "Hi Richard! Everything alright?"}""", bea.password)

		def expectedDataRichard2 = [[nickname: "BD", message: "Hi buddy! How ya doing?"], [nickname: "BDD", message: "Hi Richard! Everything alright?"]]
		List richardMessages = getActivityDetailMessages(richardResponseDetails, richard, expectedDataRichard2).responseData._embedded."yona:messages"
		def messageBob1AsSeenByRichard = richardMessages[0]
		assert messageBob1AsSeenByRichard.nickname == "BD"
		def messageBea1AsSeenByRichard = richardMessages[1]
		assert messageBea1AsSeenByRichard.nickname == "BDD"
		appService.postMessageActionWithPassword(messageBob1AsSeenByRichard._links."yona:reply".href, ["message": "Hi Bob! Doing fine!"], richard.password)
		appService.postMessageActionWithPassword(messageBea1AsSeenByRichard._links."yona:reply".href, ["message": "Hi Bea! I'm alright!"], richard.password)

		def expectedDataBob = [[nickname: "BD", message: "Hi buddy! How ya doing?"], [nickname: "RQ", message: "Hi Bob! Doing fine!"]]
		List bobMessagesRichard = getActivityDetailMessages(bobResponseDetailsRichardAsBuddy, bob, expectedDataBob).responseData._embedded."yona:messages"
		def expectedDataBea = [[nickname: "BDD", message: "Hi Richard! Everything alright?"], [nickname: "RQ", message: "Hi Bea! I'm alright!"]]
		getActivityDetailMessages(beaResponseDetailsRichardAsBuddy, bea, expectedDataBea).responseData._embedded."yona:messages"
		appService.postMessageActionWithPassword(bobMessagesRichard[1]._links."yona:reply".href, ["message": "Great buddy!"], bob.password)

		def expectedDataRichard5 = [[nickname: "BD", message: "Hi buddy! How ya doing?"], [nickname: "RQ", message: "Hi Bob! Doing fine!"], [nickname: "BD", message: "Great buddy!"], [nickname: "BDD", message: "Hi Richard! Everything alright?"], [nickname: "RQ", message: "Hi Bea! I'm alright!"]]
		List richardMessagesRevisited = getActivityDetailMessages(richardResponseDetails, richard, expectedDataRichard5, 10).responseData._embedded."yona:messages"

		then:
		richardMessagesRevisited[0].nickname == "BD"
		richardMessagesRevisited[1].nickname == "RQ (me)"
		richardMessagesRevisited[2].nickname == "BD"
		richardMessagesRevisited[3].nickname == "BDD"
		richardMessagesRevisited[4].nickname == "RQ (me)"

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
		appService.deleteUser(bea)
	}

	def 'Comment on inactive day'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob

		Goal budgetGoalNewsBuddyBob = richard.buddies[0].findActiveGoal(NEWS_ACT_CAT_URL)

		def responseDayOverviewsBobAsBuddyAll = appService.getDayActivityOverviews(richard, richard.buddies[0])
		assertResponseStatusOk(responseDayOverviewsBobAsBuddyAll)

		def responseDetailsBobAsBuddy = appService.getDayDetailsFromOverview(responseDayOverviewsBobAsBuddyAll, richard, budgetGoalNewsBuddyBob, 0, getCurrentShortDay(YonaServer.now))
		assert responseDetailsBobAsBuddy.responseData._links."yona:addComment".href
		assert responseDetailsBobAsBuddy.responseData._links."yona:messages".href

		when:
		def message = """{"message": "You're quiet!"}"""
		def responseAddMessage = addComment(responseDetailsBobAsBuddy, message, richard.password)

		then:
		assertResponseStatusOk(responseAddMessage)

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Comment on inactive week'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob

		Goal budgetGoalNewsBuddyBob = richard.buddies[0].findActiveGoal(NEWS_ACT_CAT_URL)

		def responseWeekOverviewsBobAsBuddyAll = appService.getWeekActivityOverviews(richard, richard.buddies[0])
		assertResponseStatusOk(responseWeekOverviewsBobAsBuddyAll)

		def responseDetailsBobAsBuddy = appService.getWeekDetailsFromOverview(responseWeekOverviewsBobAsBuddyAll, richard, budgetGoalNewsBuddyBob, 0)
		assert responseDetailsBobAsBuddy.responseData._links."yona:addComment".href
		assert responseDetailsBobAsBuddy.responseData._links."yona:messages".href

		when:
		def message = """{"message": "You're quiet!"}"""
		def responseAddMessage = addComment(responseDetailsBobAsBuddy, message, richard.password)

		then:
		assertResponseStatusOk(responseAddMessage)

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Richard can delete a comment of Bob on which Richard already replied'()
	{
		given:
		/** Begin of standard test setup for message delete tests **/
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob
		setCreationTime(richard, "W-1 Mon 02:18")
		setCreationTime(bob, "W-1 Mon 02:18")
		setGoalCreationTime(richard, NEWS_ACT_CAT_URL, "W-1 Mon 02:18")
		richard = appService.reloadUser(richard)
		bob = appService.reloadUser(bob)
		Goal richardGoal = richard.findActiveGoal(NEWS_ACT_CAT_URL)
		Goal bobGoalBuddyRichard = bob.buddies[0].findActiveGoal(NEWS_ACT_CAT_URL)
		def bobResponseDetailsRichardAsBuddy = appService.getDayActivityDetails(bob, bob.buddies[0], bobGoalBuddyRichard, 1, "Tue")
		def richardResponseDetails = appService.getDayActivityDetails(richard, richardGoal, 1, "Tue")

		addComment(bobResponseDetailsRichardAsBuddy, """{"message": "Hi buddy! How ya doing?"}""", bob.password)

		def expectedData1 = [[nickname: "BD", message: "Hi buddy! How ya doing?"]]
		List richardMessages = getActivityDetailMessages(richardResponseDetails, richard, expectedData1).responseData._embedded."yona:messages"
		def messageBob1AsSeenByRichard = richardMessages[0]
		assert messageBob1AsSeenByRichard.nickname == "BD"
		appService.postMessageActionWithPassword(messageBob1AsSeenByRichard._links."yona:reply".href, ["message": "Hi Bob! Doing fine!"], richard.password)

		def expectedData2 = [[nickname: "BD", message: "Hi buddy! How ya doing?"], [nickname: "RQ", message: "Hi Bob! Doing fine!"]]
		List bobMessagesRichard = getActivityDetailMessages(bobResponseDetailsRichardAsBuddy, bob, expectedData2).responseData._embedded."yona:messages"
		appService.postMessageActionWithPassword(bobMessagesRichard[1]._links."yona:reply".href, ["message": "Great buddy!"], bob.password)

		def expectedData3 = [[nickname: "BD", message: "Hi buddy! How ya doing?"], [nickname: "RQ", message: "Hi Bob! Doing fine!"], [nickname: "BD", message: "Great buddy!"]]
		List richardMessagesRevisited = getActivityDetailMessages(richardResponseDetails, richard, expectedData3, 10).responseData._embedded."yona:messages"

		assert richardMessagesRevisited[0].nickname == "BD"
		assert richardMessagesRevisited[1].nickname == "RQ (me)"
		assert richardMessagesRevisited[2].nickname == "BD"
		/** End of standard test setup for message delete tests **/

		when:
		def response = appService.deleteResourceWithPassword(richardMessagesRevisited[0]._links.edit.href, richard.password)

		then:
		assertResponseStatusOk(response)
		getActivityDetailMessages(richardResponseDetails, richard, [], 10)
		getActivityDetailMessages(bobResponseDetailsRichardAsBuddy, bob, [], 10)

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Richard can delete a comment of himself on which Bob already replied'()
	{
		given:
		/** Begin of standard test setup for message delete tests **/
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob
		setCreationTime(richard, "W-1 Mon 02:18")
		setCreationTime(bob, "W-1 Mon 02:18")
		setGoalCreationTime(richard, NEWS_ACT_CAT_URL, "W-1 Mon 02:18")
		richard = appService.reloadUser(richard)
		bob = appService.reloadUser(bob)
		Goal richardGoal = richard.findActiveGoal(NEWS_ACT_CAT_URL)
		Goal bobGoalBuddyRichard = bob.buddies[0].findActiveGoal(NEWS_ACT_CAT_URL)
		def bobResponseDetailsRichardAsBuddy = appService.getDayActivityDetails(bob, bob.buddies[0], bobGoalBuddyRichard, 1, "Tue")
		def richardResponseDetails = appService.getDayActivityDetails(richard, richardGoal, 1, "Tue")

		addComment(bobResponseDetailsRichardAsBuddy, """{"message": "Hi buddy! How ya doing?"}""", bob.password)

		def expectedData1 = [[nickname: "BD", message: "Hi buddy! How ya doing?"]]
		List richardMessages = getActivityDetailMessages(richardResponseDetails, richard, expectedData1).responseData._embedded."yona:messages"
		def messageBob1AsSeenByRichard = richardMessages[0]
		assert messageBob1AsSeenByRichard.nickname == "BD"
		appService.postMessageActionWithPassword(messageBob1AsSeenByRichard._links."yona:reply".href, ["message": "Hi Bob! Doing fine!"], richard.password)

		def expectedData2 = [[nickname: "BD", message: "Hi buddy! How ya doing?"], [nickname: "RQ", message: "Hi Bob! Doing fine!"]]
		List bobMessagesRichard = getActivityDetailMessages(bobResponseDetailsRichardAsBuddy, bob, expectedData2).responseData._embedded."yona:messages"
		appService.postMessageActionWithPassword(bobMessagesRichard[1]._links."yona:reply".href, ["message": "Great buddy!"], bob.password)

		def expectedData3 = [[nickname: "BD", message: "Hi buddy! How ya doing?"], [nickname: "RQ", message: "Hi Bob! Doing fine!"], [nickname: "BD", message: "Great buddy!"]]
		List richardMessagesRevisited = getActivityDetailMessages(richardResponseDetails, richard, expectedData3, 10).responseData._embedded."yona:messages"

		assert richardMessagesRevisited[0].nickname == "BD"
		assert richardMessagesRevisited[1].nickname == "RQ (me)"
		assert richardMessagesRevisited[2].nickname == "BD"
		/** End of standard test setup for message delete tests **/

		when:
		def response = appService.deleteResourceWithPassword(richardMessagesRevisited[1]._links.edit.href, richard.password)

		then:
		assertResponseStatusOk(response)
		getActivityDetailMessages(richardResponseDetails, richard, expectedData1, 10)
		getActivityDetailMessages(bobResponseDetailsRichardAsBuddy, bob, expectedData1, 10)

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Richard can delete a comment of Bob on which nobody replied'()
	{
		given:
		/** Begin of standard test setup for message delete tests **/
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob
		setCreationTime(richard, "W-1 Mon 02:18")
		setCreationTime(bob, "W-1 Mon 02:18")
		setGoalCreationTime(richard, NEWS_ACT_CAT_URL, "W-1 Mon 02:18")
		richard = appService.reloadUser(richard)
		bob = appService.reloadUser(bob)
		Goal richardGoal = richard.findActiveGoal(NEWS_ACT_CAT_URL)
		Goal bobGoalBuddyRichard = bob.buddies[0].findActiveGoal(NEWS_ACT_CAT_URL)
		def bobResponseDetailsRichardAsBuddy = appService.getDayActivityDetails(bob, bob.buddies[0], bobGoalBuddyRichard, 1, "Tue")
		def richardResponseDetails = appService.getDayActivityDetails(richard, richardGoal, 1, "Tue")

		addComment(bobResponseDetailsRichardAsBuddy, """{"message": "Hi buddy! How ya doing?"}""", bob.password)

		def expectedData1 = [[nickname: "BD", message: "Hi buddy! How ya doing?"]]
		List richardMessages = getActivityDetailMessages(richardResponseDetails, richard, expectedData1).responseData._embedded."yona:messages"
		def messageBob1AsSeenByRichard = richardMessages[0]
		assert messageBob1AsSeenByRichard.nickname == "BD"
		appService.postMessageActionWithPassword(messageBob1AsSeenByRichard._links."yona:reply".href, ["message": "Hi Bob! Doing fine!"], richard.password)

		def expectedData2 = [[nickname: "BD", message: "Hi buddy! How ya doing?"], [nickname: "RQ", message: "Hi Bob! Doing fine!"]]
		List bobMessagesRichard = getActivityDetailMessages(bobResponseDetailsRichardAsBuddy, bob, expectedData2).responseData._embedded."yona:messages"
		appService.postMessageActionWithPassword(bobMessagesRichard[1]._links."yona:reply".href, ["message": "Great buddy!"], bob.password)

		def expectedData3 = [[nickname: "BD", message: "Hi buddy! How ya doing?"], [nickname: "RQ", message: "Hi Bob! Doing fine!"], [nickname: "BD", message: "Great buddy!"]]
		List richardMessagesRevisited = getActivityDetailMessages(richardResponseDetails, richard, expectedData3, 10).responseData._embedded."yona:messages"

		assert richardMessagesRevisited[0].nickname == "BD"
		assert richardMessagesRevisited[1].nickname == "RQ (me)"
		assert richardMessagesRevisited[2].nickname == "BD"
		/** End of standard test setup for message delete tests **/

		when:
		def response = appService.deleteResourceWithPassword(richardMessagesRevisited[2]._links.edit.href, richard.password)

		then:
		assertResponseStatusOk(response)

		getActivityDetailMessages(richardResponseDetails, richard, expectedData2, 10)
		getActivityDetailMessages(bobResponseDetailsRichardAsBuddy, bob, expectedData2, 10)

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Richard can delete a goal after Bob commented on a related activity'()
	{
		given:
		/** Begin of standard test setup for message delete tests **/
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob
		setCreationTime(richard, "W-1 Mon 02:18")
		setCreationTime(bob, "W-1 Mon 02:18")
		setGoalCreationTime(richard, NEWS_ACT_CAT_URL, "W-1 Mon 02:18")
		richard = appService.reloadUser(richard)
		bob = appService.reloadUser(bob)
		Goal richardGoal = richard.findActiveGoal(NEWS_ACT_CAT_URL)
		Goal bobGoalBuddyRichard = bob.buddies[0].findActiveGoal(NEWS_ACT_CAT_URL)
		def bobResponseDetailsRichardAsBuddy = appService.getDayActivityDetails(bob, bob.buddies[0], bobGoalBuddyRichard, 1, "Tue")
		def richardResponseDetails = appService.getDayActivityDetails(richard, richardGoal, 1, "Tue")

		addComment(bobResponseDetailsRichardAsBuddy, """{"message": "Hi buddy! How ya doing?"}""", bob.password)

		def expectedData1 = [[nickname: "BD", message: "Hi buddy! How ya doing?"]]
		List richardMessages = getActivityDetailMessages(richardResponseDetails, richard, expectedData1).responseData._embedded."yona:messages"
		def messageBob1AsSeenByRichard = richardMessages[0]
		assert messageBob1AsSeenByRichard.nickname == "BD"
		appService.postMessageActionWithPassword(messageBob1AsSeenByRichard._links."yona:reply".href, ["message": "Hi Bob! Doing fine!"], richard.password)

		def expectedData2 = [[nickname: "BD", message: "Hi buddy! How ya doing?"], [nickname: "RQ", message: "Hi Bob! Doing fine!"]]
		List bobMessagesRichard = getActivityDetailMessages(bobResponseDetailsRichardAsBuddy, bob, expectedData2).responseData._embedded."yona:messages"
		appService.postMessageActionWithPassword(bobMessagesRichard[1]._links."yona:reply".href, ["message": "Great buddy!"], bob.password)

		def expectedData3 = [[nickname: "BD", message: "Hi buddy! How ya doing?"], [nickname: "RQ", message: "Hi Bob! Doing fine!"], [nickname: "BD", message: "Great buddy!"]]
		List richardMessagesRevisited = getActivityDetailMessages(richardResponseDetails, richard, expectedData3, 10).responseData._embedded."yona:messages"

		assert richardMessagesRevisited[0].nickname == "BD"
		assert richardMessagesRevisited[1].nickname == "RQ (me)"
		assert richardMessagesRevisited[2].nickname == "BD"
		/** End of standard test setup for message delete tests **/

		when:
		def response = appService.removeGoal(richard, richardGoal, "Don't want to monitor my news time anymore")

		then:
		assertResponseStatusNoContent(response)

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Comment using (too) long messages'(messageLength, expectedStatus)
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob

		when:
		setCreationTime(richard, "W-1 Mon 02:18")
		setCreationTime(bob, "W-1 Mon 02:18")
		setGoalCreationTime(bob, NEWS_ACT_CAT_URL, "W-1 Mon 02:18")
		reportAppActivity(bob, bob.requestingDevice, "NU.nl", "W-1 Tue 03:15", "W-1 Tue 03:35")

		richard = appService.reloadUser(richard)
		bob = appService.reloadUser(bob)
		Goal budgetGoalNewsBuddyBob = richard.buddies[0].findActiveGoal(NEWS_ACT_CAT_URL)
		assert budgetGoalNewsBuddyBob
		def responseOverviewsBobAsBuddyAll = appService.getDayActivityOverviews(richard, richard.buddies[0], ["size": 14])
		assertResponseStatusOk(responseOverviewsBobAsBuddyAll)
		def responseDetailsBobAsBuddy = appService.getDayDetailsFromOverview(responseOverviewsBobAsBuddyAll, richard, budgetGoalNewsBuddyBob, 1, "Tue")
		assertResponseStatusOk(responseDetailsBobAsBuddy)
		def messageText = StringUtils.repeat("*", messageLength)
		def messageJson = """{"message": "$messageText"}"""

		then:
		def response = addComment(responseDetailsBobAsBuddy, messageJson, richard.password)
		assertResponseStatus(response, expectedStatus)
		expectedStatus == 200 || response.responseData.message == "The string 'message' is too long. It contains $messageLength characters while only 200 are allowed"

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)

		where:
		messageLength | expectedStatus
		200           | 200
		201           | 400
	}

	def addComment(response, message, password)
	{
		addComment(appService, response, message, password)
	}

	static addComment(appService, response, message, password)
	{
		appService.yonaServer.createResourceWithPassword(response.responseData._links."yona:addComment".href, message, password)
	}

	void assertCommentingWorks(User richard, User bob, boolean isWeek, Closure userOverviewRetriever, Closure buddyOverviewRetriever, Closure detailsRetriever)
	{
		assertCommentingWorks(appService, richard, bob, NEWS_ACT_CAT_URL, isWeek, userOverviewRetriever, buddyOverviewRetriever, detailsRetriever, { User user, message -> assertMarkReadUnread(user, message) })
	}

	private getActivityDetailMessages(responseGetActivityDetails, User user, expectedData, int pageSize = 4)
	{
		return getActivityDetailMessages(appService, responseGetActivityDetails, user, expectedData, pageSize)
	}

	static void assertCommentingWorks(AppService appService, User richard, User bob, def activityCategoryUrl, boolean isWeek, Closure userOverviewRetriever, Closure buddyOverviewRetriever, Closure detailsRetriever,
									  Closure assertMarkReadUnread)
	{
		Goal budgetGoalNewsBuddyBob = richard.buddies[0].findActiveGoal(activityCategoryUrl)
		assert budgetGoalNewsBuddyBob
		Goal budgetGoalNewsBob = bob.findActiveGoal(activityCategoryUrl)
		assert budgetGoalNewsBob

		def responseOverviewsBobAsBuddyAll = buddyOverviewRetriever(richard)
		assertResponseStatusOk(responseOverviewsBobAsBuddyAll)

		def responseDetailsBobAsBuddy = detailsRetriever(responseOverviewsBobAsBuddyAll, richard, budgetGoalNewsBuddyBob)
		assert responseDetailsBobAsBuddy.responseData._links."yona:addComment".href
		assert responseDetailsBobAsBuddy.responseData._links."yona:messages".href

		appService.clearLastFirebaseMessage(richard.requestingDevice.firebaseInstanceId)
		appService.clearLastFirebaseMessage(bob.requestingDevice.firebaseInstanceId)
		def message = """{"message": "You're quiet!"}"""
		def responseAddMessage = addComment(appService, responseDetailsBobAsBuddy, message, richard.password)

		assertResponseStatusOk(responseAddMessage)
		def addedMessage = responseAddMessage.responseData
		assertCommentMessageDetails(addedMessage, richard, isWeek, richard, responseDetailsBobAsBuddy.responseData._links.self.href, "You're quiet!", addedMessage)

		assertResponseStatus(appService.clearLastFirebaseMessage(richard.requestingDevice.firebaseInstanceId), 404)
		// No message sent since last clear
		assertResponseStatusOk(appService.clearLastFirebaseMessage(bob.requestingDevice.firebaseInstanceId))
		// Message sent since last clear

		assertMarkReadUnread(richard, addedMessage)

		def expectedData1 = [[nickname: richard.nickname, message: "You're quiet!"]]
		def responseInitialGetCommentMessagesSeenByRichard = getActivityDetailMessages(appService, responseDetailsBobAsBuddy, richard, expectedData1)
		List initialMessagesSeenByRichard = responseInitialGetCommentMessagesSeenByRichard.responseData._embedded."yona:messages"
		assertCommentMessageDetails(initialMessagesSeenByRichard[0], richard, isWeek, richard, responseDetailsBobAsBuddy.responseData._links.self.href, "You're quiet!", initialMessagesSeenByRichard[0])

		def responseOverviewsBobAll = userOverviewRetriever(bob)
		assertResponseStatusOk(responseOverviewsBobAll)

		def responseDetailsBob = detailsRetriever(responseOverviewsBobAll, bob, budgetGoalNewsBob)
		assert responseDetailsBob.responseData._links."yona:addComment" == null
		assert responseDetailsBob.responseData._links."yona:messages".href

		def responseInitialGetCommentMessagesSeenByBob = getActivityDetailMessages(appService, responseDetailsBob, bob, expectedData1)
		List initialMessagesSeenByBob = responseInitialGetCommentMessagesSeenByBob.responseData._embedded."yona:messages"
		assertCommentMessageDetails(initialMessagesSeenByBob[0], bob, isWeek, bob.buddies[0], responseDetailsBob.responseData._links.self.href, "You're quiet!", initialMessagesSeenByBob[0])

		replyToMessage(appService, initialMessagesSeenByBob[0], bob, "My battery died :)", isWeek, responseDetailsBob, initialMessagesSeenByBob[0])

		def expectedData2 = [[nickname: richard.nickname, message: "You're quiet!"], [nickname: bob.nickname, message: "My battery died :)"]]
		def responseSecondGetCommentMessagesSeenByRichard = getActivityDetailMessages(appService, responseDetailsBobAsBuddy, richard, expectedData2)
		List replyMessagesSeenByRichard = responseSecondGetCommentMessagesSeenByRichard.responseData._embedded."yona:messages"
		assertCommentMessageDetails(replyMessagesSeenByRichard[1], richard, isWeek, richard.buddies[0], responseDetailsBobAsBuddy.responseData._links.self.href, "My battery died :)",
				replyMessagesSeenByRichard[0], responseSecondGetCommentMessagesSeenByRichard.responseData._embedded."yona:messages"[0])

		replyToMessage(appService, replyMessagesSeenByRichard[1], richard, "Too bad!", isWeek, responseDetailsBobAsBuddy, replyMessagesSeenByRichard[0])

		def expectedData3 = [[nickname: richard.nickname, message: "You're quiet!"], [nickname: bob.nickname, message: "My battery died :)"], [nickname: richard.nickname, message: "Too bad!"],]
		def responseSecondGetCommentMessagesSeenByBob = getActivityDetailMessages(appService, responseDetailsBob, bob, expectedData3)
		List secondReplyMessagesSeenByBob = responseSecondGetCommentMessagesSeenByBob.responseData._embedded."yona:messages"
		assertCommentMessageDetails(secondReplyMessagesSeenByBob[2], bob, isWeek, bob.buddies[0], responseDetailsBob.responseData._links.self.href, "Too bad!", secondReplyMessagesSeenByBob[0], secondReplyMessagesSeenByBob[1])

		replyToMessage(appService, secondReplyMessagesSeenByBob[2], bob, "Yes, it is...", isWeek, responseDetailsBob, secondReplyMessagesSeenByBob[0])

		def expectedData4 = [[nickname: richard.nickname, message: "You're quiet!"], [nickname: bob.nickname, message: "My battery died :)"], [nickname: richard.nickname, message: "Too bad!"], [nickname: bob.nickname, message: "Yes, it is..."]]
		def responseThirdGetCommentMessagesSeenByRichard = getActivityDetailMessages(appService, responseDetailsBobAsBuddy, richard, expectedData4)
		List replyToReplyMessagesSeenByRichard = responseThirdGetCommentMessagesSeenByRichard.responseData._embedded."yona:messages"
		assertCommentMessageDetails(replyToReplyMessagesSeenByRichard[3], richard, isWeek, richard.buddies[0], responseDetailsBobAsBuddy.responseData._links.self.href, "Yes, it is...", replyToReplyMessagesSeenByRichard[0], replyToReplyMessagesSeenByRichard[2])

		replyToMessage(appService, replyToReplyMessagesSeenByRichard[3], richard, "No budget for a new one?", isWeek, responseDetailsBobAsBuddy, replyToReplyMessagesSeenByRichard[0])

		def expectedData5 = [[nickname: richard.nickname, message: "You're quiet!"], [nickname: bob.nickname, message: "My battery died :)"], [nickname: richard.nickname, message: "Too bad!"], [nickname: bob.nickname, message: "Yes, it is..."], [nickname: richard.nickname, message: "No budget for a new one?"]]
		def responseFinalGetCommentMessagesSeenByRichard = getActivityDetailMessages(appService, responseDetailsBobAsBuddy, richard, expectedData5)
		List messagesRichard = responseFinalGetCommentMessagesSeenByRichard.responseData._embedded."yona:messages"
		assertCommentMessageDetails(messagesRichard[0], richard, isWeek, richard, responseDetailsBobAsBuddy.responseData._links.self.href, "You're quiet!", messagesRichard[0])
		assertCommentMessageDetails(messagesRichard[1], richard, isWeek, richard.buddies[0], responseDetailsBobAsBuddy.responseData._links.self.href, "My battery died :)", messagesRichard[0], messagesRichard[0])
		assertCommentMessageDetails(messagesRichard[2], richard, isWeek, richard, responseDetailsBobAsBuddy.responseData._links.self.href, "Too bad!", messagesRichard[0], messagesRichard[1])
		assertCommentMessageDetails(messagesRichard[3], richard, isWeek, richard.buddies[0], responseDetailsBobAsBuddy.responseData._links.self.href, "Yes, it is...", messagesRichard[0], messagesRichard[2])
		assertNextPage(appService, responseFinalGetCommentMessagesSeenByRichard, richard)

		def responseFinalGetCommentMessagesSeenByBob = getActivityDetailMessages(appService, responseDetailsBob, bob, expectedData5)
		List messagesBob = responseFinalGetCommentMessagesSeenByBob.responseData._embedded."yona:messages"
		assertCommentMessageDetails(messagesBob[0], bob, isWeek, bob.buddies[0], responseDetailsBob.responseData._links.self.href, "You're quiet!", messagesBob[0])
		assertCommentMessageDetails(messagesBob[1], bob, isWeek, bob, responseDetailsBob.responseData._links.self.href, "My battery died :)", messagesBob[0], messagesBob[0])
		assertCommentMessageDetails(messagesBob[2], bob, isWeek, bob.buddies[0], responseDetailsBob.responseData._links.self.href, "Too bad!", messagesBob[0], messagesBob[1])
		assertCommentMessageDetails(messagesBob[3], bob, isWeek, bob, responseDetailsBob.responseData._links.self.href, "Yes, it is...", messagesBob[0], messagesBob[2])
		assertNextPage(appService, responseFinalGetCommentMessagesSeenByBob, bob)

		def allMessagesRichardResponse = appService.getMessages(richard)
		assert allMessagesRichardResponse.responseData._embedded?."yona:messages"?.findAll { it."@type" == "ActivityCommentMessage" }?.size() == 2
		def activityCommentMessagesRichard = allMessagesRichardResponse.responseData._embedded?."yona:messages"?.findAll { it."@type" == "ActivityCommentMessage" }
		assertCommentMessageDetails(activityCommentMessagesRichard[0], richard, isWeek, richard.buddies[0], responseDetailsBobAsBuddy.responseData._links.self.href, "Yes, it is...", messagesRichard[0], messagesRichard[2])
		assertCommentMessageDetails(activityCommentMessagesRichard[1], richard, isWeek, richard.buddies[0], responseDetailsBobAsBuddy.responseData._links.self.href, "My battery died :)", messagesRichard[0], messagesRichard[0])

		def allMessagesBobResponse = appService.getMessages(bob)
		assert allMessagesBobResponse.responseData._embedded?."yona:messages"?.findAll { it."@type" == "ActivityCommentMessage" }?.size() == 3
		def activityCommentMessagesBob = allMessagesBobResponse.responseData._embedded?."yona:messages"?.findAll { it."@type" == "ActivityCommentMessage" }
		assertCommentMessageDetails(activityCommentMessagesBob[0], bob, isWeek, bob.buddies[0], responseDetailsBob.responseData._links.self.href, "No budget for a new one?", activityCommentMessagesBob[2], messagesBob[3])
		assertCommentMessageDetails(activityCommentMessagesBob[1], bob, isWeek, bob.buddies[0], responseDetailsBob.responseData._links.self.href, "Too bad!", activityCommentMessagesBob[2], messagesBob[1])
		assertCommentMessageDetails(activityCommentMessagesBob[2], bob, isWeek, bob.buddies[0], responseDetailsBob.responseData._links.self.href, "You're quiet!", activityCommentMessagesBob[2])
	}

	private static void replyToMessage(AppService appService, messageToReply, User senderUser, messageToSend, boolean isWeek, responseGetActivityDetails, threadHeadMessage)
	{
		def responseReplyFromBob = appService.postMessageActionWithPassword(messageToReply._links."yona:reply".href, ["message": messageToSend], senderUser.password)
		assertResponseStatusOk(responseReplyFromBob)
		assert responseReplyFromBob.responseData.properties["status"] == "done"
		assert responseReplyFromBob.responseData._embedded?."yona:affectedMessages"?.size() == 1
		def replyMessage = responseReplyFromBob.responseData._embedded."yona:affectedMessages"[0]
		assertCommentMessageDetails(replyMessage, senderUser, isWeek, senderUser, responseGetActivityDetails.responseData._links.self.href, messageToSend, threadHeadMessage, messageToReply)
	}

	private static getActivityDetailMessages(AppService appService, responseGetActivityDetails, User user, List expectedData, int pageSize = 4)
	{
		int expectedNumMessages = expectedData.size
		int expectedNumMessagesInPage = Math.min(expectedNumMessages, pageSize)
		def response = appService.yonaServer.getJsonWithPassword(responseGetActivityDetails.responseData._links."yona:messages".href, user.password, ["size": pageSize])

		assertResponseStatusOk(response)
		def messages = response.responseData?._embedded?."yona:messages"
		if (expectedNumMessagesInPage == 0)
		{
			assert messages == null
		}
		else
		{
			assert messages.size() == expectedNumMessagesInPage
		}
		messages.eachWithIndex { message, i ->
			assert message.nickname == expectedData[i].nickname + ((expectedData[i].nickname == user.nickname) ? " (me)" : "")
			assert message.message == expectedData[i].message
		}
		assert response.responseData.page.size == pageSize
		assert response.responseData.page.totalElements == expectedNumMessages
		assert response.responseData.page.totalPages == Math.ceil(expectedNumMessages / pageSize)
		assert response.responseData.page.number == 0

		assert response.responseData._links?.prev?.href == null
		if (expectedNumMessages > pageSize)
		{
			assert response.responseData._links?.next?.href
		}

		return response
	}

	private static void assertNextPage(AppService appService, responseGetActivityDetails, User user)
	{
		int defaultPageSize = 4
		def response = appService.yonaServer.getJsonWithPassword(responseGetActivityDetails.responseData._links.next.href, user.password)

		assertResponseStatusOk(response)
		assert response.responseData?._embedded?."yona:messages"?.size() == 1
		assert response.responseData.page.size == defaultPageSize
		assert response.responseData.page.totalElements == 5
		assert response.responseData.page.totalPages == 2
		assert response.responseData.page.number == 1

		assert response.responseData._links?.prev?.href
		assert response.responseData._links?.next?.href == null
	}

	private static void assertCommentMessageDetails(message, User user, boolean isWeek, sender, expectedDetailsUrl, expectedText, threadHeadMessage, repliedMessage = null)
	{
		def expectedNickname = (sender instanceof User) ? sender.nickname : sender.user.nickname
		assert message."@type" == "ActivityCommentMessage"
		assert message.message == expectedText
		assert message.nickname == ((user.url == sender.url) ? (expectedNickname + " (me)") : expectedNickname)

		assert message._links?.self?.href?.startsWith(YonaServer.stripQueryString(user.messagesUrl))
		assert message._links?.edit?.href == message._links.self.href
		assert message._links?."yona:threadHead"?.href == threadHeadMessage._links.self.href

		def expectedLinks = BASE_LINKS

		if (repliedMessage)
		{
			assert repliedMessage._links.self.href == message._links?."yona:repliedMessage"?.href
			expectedLinks += "yona:repliedMessage"
		}

		if (isWeek)
		{
			expectedLinks += "yona:weekDetails"
			assert message._links?."yona:weekDetails"?.href == expectedDetailsUrl
		}
		else
		{
			expectedLinks += "yona:dayDetails"
			assert message._links?."yona:dayDetails"?.href == expectedDetailsUrl
		}
		if (sender instanceof Buddy)
		{
			expectedLinks += ["yona:buddy", "yona:reply"]
			assert message._links?."yona:buddy"?.href == sender.url
			assert message._links?."yona:reply"?.href?.startsWith(YonaServer.stripQueryString(user.url))
		}
		else
		{
			assert message._links?."yona:user"?.href == sender.url - ~/&requestingDeviceId.*/
		}

		assert message._links.keySet() - "curies" == expectedLinks
	}
}
