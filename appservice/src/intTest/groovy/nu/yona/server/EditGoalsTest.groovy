/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import java.time.Duration
import java.time.ZoneOffset
import java.time.ZonedDateTime

import groovy.json.*
import nu.yona.server.test.AppActivity
import nu.yona.server.test.BudgetGoal
import nu.yona.server.test.Goal
import nu.yona.server.test.TimeZoneGoal
import nu.yona.server.test.User

class EditGoalsTest extends AbstractAppServiceIntegrationTest
{
	def 'Validation: Try to add goal for not existing category'()
	{
		given:
		def richard = addRichard()

		when:
		def notExistingActivityCategoryUrl = SOCIAL_ACT_CAT_URL.substring(0, SOCIAL_ACT_CAT_URL.lastIndexOf('/')) + "/" + UUID.randomUUID()
		def response = appService.addGoal(richard, BudgetGoal.createInstance(notExistingActivityCategoryUrl, 60))

		then:
		response.status == 404
		response.responseData.code == "error.activitycategory.not.found"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Validation: Try to add second goal for activity category'()
	{
		given:
		def richard = addRichard()

		when:
		def response = appService.addGoal(richard, BudgetGoal.createInstance(GAMBLING_ACT_CAT_URL, 60))

		then:
		response.status == 400
		response.responseData.code == "error.goal.cannot.add.second.on.activity.category"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Get goals'()
	{
		given:
		def richard = addRichard()
		def creationTime = YonaServer.now

		when:
		def response = appService.getGoals(richard)

		then:
		response.status == 200
		response.responseData._embedded."yona:goals".size() == 2
		def gamblingGoals = filterGoals(response, GAMBLING_ACT_CAT_URL)
		gamblingGoals.size() == 1
		gamblingGoals[0]."@type" == "BudgetGoal"
		!gamblingGoals[0]._links.edit //mandatory goal
		assertEquals(gamblingGoals[0].creationTime, ZonedDateTime.of(2017,1,1,12,0,0,0, ZoneOffset.UTC))
		def newsGoals = filterGoals(response, NEWS_ACT_CAT_URL)
		newsGoals.size() == 1
		newsGoals[0]."@type" == "BudgetGoal"
		newsGoals[0]._links.edit.href
		assertEquals(newsGoals[0].creationTime, creationTime)

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Add budget goal'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		def richard = richardAndBob.richard
		def bob = richardAndBob.bob

		when:
		BudgetGoal addedGoal = appService.addGoal(appService.&assertResponseStatusCreated, richard, BudgetGoal.createInstance(SOCIAL_ACT_CAT_URL, 60), "Going to monitor my social time!")

		then:
		addedGoal
		addedGoal.maxDurationMinutes == 60

		def responseGoalsAfterAdd = appService.getGoals(richard)
		responseGoalsAfterAdd.status == 200
		responseGoalsAfterAdd.responseData._embedded."yona:goals".size() == 3

		def bobMessagesResponse = appService.getMessages(bob)
		def goalChangeMessages = bobMessagesResponse.responseData._embedded."yona:messages".findAll
		{ it."@type" == "GoalChangeMessage" }
		goalChangeMessages.size() == 1
		goalChangeMessages[0].change == 'GOAL_ADDED'
		goalChangeMessages[0]._links.related.href == SOCIAL_ACT_CAT_URL
		goalChangeMessages[0]._links?."yona:buddy"?.href == bob.buddies[0].url
		goalChangeMessages[0]._links?."yona:user"?.href == richard.url
		goalChangeMessages[0]._embedded?."yona:user" == null
		goalChangeMessages[0].nickname == 'RQ'
		assertEquals(goalChangeMessages[0].creationTime, YonaServer.now)
		goalChangeMessages[0].message == "Going to monitor my social time!"
		goalChangeMessages[0]._links.edit

		assertMarkReadUnread(bob, goalChangeMessages[0])

		def goal = findActiveGoal(responseGoalsAfterAdd, SOCIAL_ACT_CAT_URL)
		goal.maxDurationMinutes == 60
		goal.spreadCells == null
		goal.historyItem == false

		def richardMessagesResponse = appService.getMessages(richard)
		richardMessagesResponse.responseData._embedded."yona:messages".findAll
				{ it."@type" == "GoalChangeMessage" }.size() == 0

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Add time zone goal'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob

		when:
		TimeZoneGoal addedGoal = appService.addGoal(appService.&assertResponseStatusCreated, richard, TimeZoneGoal.createInstance(SOCIAL_ACT_CAT_URL, ["11:00-12:00"].toArray()), "Going to restrict my social time!")

		then:
		addedGoal
		addedGoal.zones
		addedGoal.zones.size() == 1

		def responseGoalsAfterAdd = appService.getGoals(richard)
		responseGoalsAfterAdd.status == 200
		responseGoalsAfterAdd.responseData._embedded."yona:goals".size() == 3

		def bobMessagesResponse = appService.getMessages(bob)
		def goalChangeMessages = bobMessagesResponse.responseData._embedded."yona:messages".findAll
		{ it."@type" == "GoalChangeMessage" }
		goalChangeMessages.size() == 1
		goalChangeMessages[0].change == 'GOAL_ADDED'
		goalChangeMessages[0]._links.related.href == SOCIAL_ACT_CAT_URL
		goalChangeMessages[0]._links?."yona:buddy"?.href == bob.buddies[0].url
		goalChangeMessages[0]._links?."yona:user"?.href == richard.url
		goalChangeMessages[0]._embedded?."yona:user" == null
		goalChangeMessages[0].nickname == 'RQ'
		assertEquals(goalChangeMessages[0].creationTime, YonaServer.now)
		goalChangeMessages[0].message == "Going to restrict my social time!"
		goalChangeMessages[0]._links.edit

		def goal = findActiveGoal(responseGoalsAfterAdd, SOCIAL_ACT_CAT_URL)
		goal.maxDurationMinutes == null
		goal.spreadCells == [44, 45, 46, 47]
		goal.historyItem == false

		def richardMessagesResponse = appService.getMessages(richard)
		richardMessagesResponse.responseData._embedded."yona:messages".findAll
				{ it."@type" == "GoalChangeMessage" }.size() == 0

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Add full-day time zone goal'()
	{
		given:
		User richard = addRichard()

		when:
		TimeZoneGoal addedGoal = appService.addGoal(appService.&assertResponseStatusCreated, richard, TimeZoneGoal.createInstance(SOCIAL_ACT_CAT_URL, ["00:00-24:00"].toArray()), "Going social!")

		then:
		addedGoal
		addedGoal.zones?.size() == 1
		addedGoal.spreadCells == (0..<24*4).collect()

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Add first hour time zone goal'()
	{
		given:
		User richard = addRichard()

		when:
		TimeZoneGoal addedGoal = appService.addGoal(appService.&assertResponseStatusCreated, richard, TimeZoneGoal.createInstance(SOCIAL_ACT_CAT_URL, ["00:00-01:00"].toArray()))

		then:
		addedGoal
		addedGoal.zones?.size() == 1
		addedGoal.spreadCells == (0..<1*4).collect()

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Add last hour time zone goal'()
	{
		given:
		User richard = addRichard()

		when:
		TimeZoneGoal addedGoal = appService.addGoal(appService.&assertResponseStatusCreated, richard, TimeZoneGoal.createInstance(SOCIAL_ACT_CAT_URL, ["23:00-24:00"].toArray()))

		then:
		addedGoal
		addedGoal.zones?.size() == 1
		addedGoal.spreadCells == (23*4..<24*4).collect()

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Add max zones time zone goal'()
	{
		given:
		User richard = addRichard()
		def allZones = [
			"00:00-00:15",
			"00:15-00:30",
			"00:30-00:45",
			"00:45-01:00",
			"01:00-01:15",
			"01:15-01:30",
			"01:30-01:45",
			"01:45-02:00",
			"02:00-02:15",
			"02:15-02:30",
			"02:30-02:45",
			"02:45-03:00",
			"03:00-03:15",
			"03:15-03:30",
			"03:30-03:45",
			"03:45-04:00",
			"04:00-04:15",
			"04:15-04:30",
			"04:30-04:45",
			"04:45-05:00",
			"05:00-05:15",
			"05:15-05:30",
			"05:30-05:45",
			"05:45-06:00",
			"06:00-06:15",
			"06:15-06:30",
			"06:30-06:45",
			"06:45-07:00",
			"07:00-07:15",
			"07:15-07:30",
			"07:30-07:45",
			"07:45-08:00",
			"08:00-08:15",
			"08:15-08:30",
			"08:30-08:45",
			"08:45-09:00",
			"09:00-09:15",
			"09:15-09:30",
			"09:30-09:45",
			"09:45-10:00",
			"10:00-10:15",
			"10:15-10:30",
			"10:30-10:45",
			"10:45-11:00",
			"11:00-11:15",
			"11:15-11:30",
			"11:30-11:45",
			"11:45-12:00",
			"12:00-12:15",
			"12:15-12:30",
			"12:30-12:45",
			"12:45-13:00",
			"13:00-13:15",
			"13:15-13:30",
			"13:30-13:45",
			"13:45-14:00",
			"14:00-14:15",
			"14:15-14:30",
			"14:30-14:45",
			"14:45-15:00",
			"15:00-15:15",
			"15:15-15:30",
			"15:30-15:45",
			"15:45-16:00",
			"16:00-16:15",
			"16:15-16:30",
			"16:30-16:45",
			"16:45-17:00",
			"17:00-17:15",
			"17:15-17:30",
			"17:30-17:45",
			"17:45-18:00",
			"18:00-18:15",
			"18:15-18:30",
			"18:30-18:45",
			"18:45-19:00",
			"19:00-19:15",
			"19:15-19:30",
			"19:30-19:45",
			"19:45-20:00",
			"20:00-20:15",
			"20:15-20:30",
			"20:30-20:45",
			"20:45-21:00",
			"21:00-21:15",
			"21:15-21:30",
			"21:30-21:45",
			"21:45-22:00",
			"22:00-22:15",
			"22:15-22:30",
			"22:30-22:45",
			"22:45-23:00",
			"23:00-23:15",
			"23:15-23:30",
			"23:30-23:45",
			"23:45-24:00"
		].toArray()

		when:
		TimeZoneGoal addedGoal = appService.addGoal(appService.&assertResponseStatusCreated, richard, TimeZoneGoal.createInstance(SOCIAL_ACT_CAT_URL, allZones))

		then:
		addedGoal
		addedGoal.zones?.size() == 24 * 4
		addedGoal.spreadCells == (0..<24*4).collect()

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Update budget goal'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		def richard = richardAndBob.richard
		def bob = richardAndBob.bob
		BudgetGoal addedGoal = addBudgetGoal(richard, SOCIAL_ACT_CAT_URL, 60, "W-1 Thu 18:00")
		postFacebookActivityPastHour(richard)
		BudgetGoal updatedGoal = BudgetGoal.createInstance(SOCIAL_ACT_CAT_URL, 120)
		bob = appService.reloadUser(bob)
		def buddyRichardUrl = bob.buddies[0].url

		when:
		def response = appService.updateGoal(richard, addedGoal.url, updatedGoal, "Want to become a bit more social :)")

		then:
		response.status == 200
		response.responseData.maxDurationMinutes == 120

		def responseGoalsAfterUpdate = appService.getGoals(richard)
		responseGoalsAfterUpdate.status == 200
		responseGoalsAfterUpdate.responseData._embedded."yona:goals".size() == 4
		findActiveGoal(responseGoalsAfterUpdate, SOCIAL_ACT_CAT_URL).maxDurationMinutes == 120
		assertEquals(findActiveGoal(responseGoalsAfterUpdate, SOCIAL_ACT_CAT_URL).creationTime, YonaServer.now)

		def allSocialGoals = findGoalsIncludingHistoryItems(responseGoalsAfterUpdate, SOCIAL_ACT_CAT_URL)
		allSocialGoals.size() == 2
		def historyItem = allSocialGoals.find
		{ it.historyItem }
		historyItem.maxDurationMinutes == 60

		def bobMessagesResponse = appService.getMessages(bob)
		def goalChangeMessages = bobMessagesResponse.responseData._embedded."yona:messages".findAll
		{ it."@type" == "GoalChangeMessage" }
		goalChangeMessages.size() == 2
		goalChangeMessages[0].change == 'GOAL_CHANGED'
		goalChangeMessages[0]._links.related.href == SOCIAL_ACT_CAT_URL
		goalChangeMessages[0]._links?."yona:buddy"?.href == bob.buddies[0].url
		goalChangeMessages[0]._links?."yona:user"?.href == richard.url
		goalChangeMessages[0]._embedded?."yona:user" == null
		goalChangeMessages[0].nickname == 'RQ'
		assertEquals(goalChangeMessages[0].creationTime, YonaServer.now)
		goalChangeMessages[0].message == "Want to become a bit more social :)"
		goalChangeMessages[0]._links.edit
		goalChangeMessages[1].change == 'GOAL_ADDED'
		goalChangeMessages[1]._links?."yona:buddy"?.href == bob.buddies[0].url

		def richardMessagesResponse = appService.getMessages(richard)
		richardMessagesResponse.responseData._embedded."yona:messages".findAll
				{ it."@type" == "GoalChangeMessage" }.size() == 0

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Update time zone goal'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		def richard = richardAndBob.richard
		def bob = richardAndBob.bob
		TimeZoneGoal addedGoal = appService.addGoal(appService.&assertResponseStatusCreated, richard, TimeZoneGoal.createInstance(SOCIAL_ACT_CAT_URL, ["10:00-11:00"].toArray()), "Going to restrict my social time!")
		postFacebookActivityPastHour(richard)
		TimeZoneGoal updatedGoal = TimeZoneGoal.createInstance(SOCIAL_ACT_CAT_URL, ["11:00-12:00", "20:00-22:00"].toArray())

		when:
		def response = appService.updateGoal(richard, addedGoal.url, updatedGoal, "Will be social in the evening too")

		then:
		response.status == 200
		response.responseData.zones.size() == 2

		def responseGoalsAfterUpdate = appService.getGoals(richard)
		responseGoalsAfterUpdate.status == 200
		responseGoalsAfterUpdate.responseData._embedded."yona:goals".size() == 4
		def timeZoneGoalAfterUpdate = findActiveGoal(responseGoalsAfterUpdate, SOCIAL_ACT_CAT_URL)
		timeZoneGoalAfterUpdate.zones.size() == 2
		timeZoneGoalAfterUpdate.spreadCells == [44, 45, 46, 47, 80, 81, 82, 83, 84, 85, 86, 87]

		def allSocialGoals = findGoalsIncludingHistoryItems(responseGoalsAfterUpdate, SOCIAL_ACT_CAT_URL)
		allSocialGoals.size() == 2
		def historyItem = allSocialGoals.find
		{ it.historyItem }
		historyItem.zones.size() == 1
		historyItem.spreadCells == [40, 41, 42, 43]

		def bobMessagesResponse = appService.getMessages(bob)
		def goalChangeMessages = bobMessagesResponse.responseData._embedded."yona:messages".findAll
		{ it."@type" == "GoalChangeMessage" }
		goalChangeMessages.size() == 2
		goalChangeMessages[0].change == 'GOAL_CHANGED'
		goalChangeMessages[0]._links.related.href == SOCIAL_ACT_CAT_URL
		goalChangeMessages[0]._links?."yona:buddy"?.href == bob.buddies[0].url
		goalChangeMessages[0]._links?."yona:user"?.href == richard.url
		goalChangeMessages[0]._embedded?."yona:user" == null
		goalChangeMessages[0].nickname == 'RQ'
		assertEquals(goalChangeMessages[0].creationTime, YonaServer.now)
		goalChangeMessages[0].message == "Will be social in the evening too"
		goalChangeMessages[0]._links.edit
		goalChangeMessages[1].change == 'GOAL_ADDED'

		def richardMessagesResponse = appService.getMessages(richard)
		richardMessagesResponse.responseData._embedded."yona:messages".findAll
				{ it."@type" == "GoalChangeMessage" }.size() == 0

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Update goal multilpe times'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		def richard = richardAndBob.richard
		def bob = richardAndBob.bob
		BudgetGoal addedGoal = addBudgetGoal(richard, SOCIAL_ACT_CAT_URL, 60, "W-1 Thu 18:00")
		postFacebookActivityPastHour(richard)
		BudgetGoal updatedGoal = BudgetGoal.createInstance(SOCIAL_ACT_CAT_URL, 120)
		bob = appService.reloadUser(bob)
		def buddyRichardUrl = bob.buddies[0].url

		// Change 1
		when:
		def response = appService.updateGoal(richard, addedGoal.url, updatedGoal, "Want to become a bit more social :)")

		then:
		response.status == 200
		response.responseData.maxDurationMinutes == 120

		def responseGoalsAfterUpdate = appService.getGoals(richard)
		responseGoalsAfterUpdate.status == 200
		responseGoalsAfterUpdate.responseData._embedded."yona:goals".size() == 4
		findActiveGoal(responseGoalsAfterUpdate, SOCIAL_ACT_CAT_URL).maxDurationMinutes == 120
		assertEquals(findActiveGoal(responseGoalsAfterUpdate, SOCIAL_ACT_CAT_URL).creationTime, YonaServer.now)

		def allSocialGoals = findGoalsIncludingHistoryItems(responseGoalsAfterUpdate, SOCIAL_ACT_CAT_URL)
		allSocialGoals.size() == 2
		def historyItem = allSocialGoals.find
		{ it.historyItem }
		historyItem.maxDurationMinutes == 60

		def bobMessagesResponse = appService.getMessages(bob)
		def goalChangeMessages = bobMessagesResponse.responseData._embedded."yona:messages".findAll
		{ it."@type" == "GoalChangeMessage" }
		goalChangeMessages.size() == 2
		goalChangeMessages[0].change == 'GOAL_CHANGED'
		goalChangeMessages[0]._links.related.href == SOCIAL_ACT_CAT_URL
		goalChangeMessages[0]._links?."yona:buddy"?.href == bob.buddies[0].url
		goalChangeMessages[0]._links?."yona:user"?.href == richard.url
		goalChangeMessages[0]._embedded?."yona:user" == null
		goalChangeMessages[0].nickname == 'RQ'
		assertEquals(goalChangeMessages[0].creationTime, YonaServer.now)
		goalChangeMessages[0].message == "Want to become a bit more social :)"
		goalChangeMessages[0]._links.edit
		goalChangeMessages[1].change == 'GOAL_ADDED'
		goalChangeMessages[1]._links?."yona:buddy"?.href == bob.buddies[0].url

		def richardMessagesResponse = appService.getMessages(richard)
		richardMessagesResponse.responseData._embedded."yona:messages".findAll
				{ it."@type" == "GoalChangeMessage" }.size() == 0

		// Change 2
		when:
		BudgetGoal updatedGoal2 = BudgetGoal.createInstance(SOCIAL_ACT_CAT_URL, 180)
		def response2 = appService.updateGoal(richard, addedGoal.url, updatedGoal2, "Want to become even more social :)")

		then:
		response2.status == 200
		response2.responseData.maxDurationMinutes == 180

		def responseGoalsAfterUpdate2 = appService.getGoals(richard)
		responseGoalsAfterUpdate2.status == 200
		responseGoalsAfterUpdate2.responseData._embedded."yona:goals".size() == 5
		findActiveGoal(responseGoalsAfterUpdate2, SOCIAL_ACT_CAT_URL).maxDurationMinutes == 180
		assertEquals(findActiveGoal(responseGoalsAfterUpdate, SOCIAL_ACT_CAT_URL).creationTime, YonaServer.now)

		def allSocialGoals2 = findGoalsIncludingHistoryItems(responseGoalsAfterUpdate2, SOCIAL_ACT_CAT_URL)
		allSocialGoals2.size() == 3
		def historyItem2 = allSocialGoals.find
		{ it.historyItem }
		historyItem2.maxDurationMinutes == 60

		def bobMessagesResponse2 = appService.getMessages(bob)
		def goalChangeMessages2 = bobMessagesResponse2.responseData._embedded."yona:messages".findAll
		{ it."@type" == "GoalChangeMessage" }
		goalChangeMessages2.size() == 3
		goalChangeMessages2[0].change == 'GOAL_CHANGED'
		goalChangeMessages2[0]._links.related.href == SOCIAL_ACT_CAT_URL
		goalChangeMessages2[0]._links?."yona:buddy"?.href == bob.buddies[0].url
		goalChangeMessages2[0]._links?."yona:user"?.href == richard.url
		goalChangeMessages2[0]._embedded?."yona:user" == null
		goalChangeMessages2[0].nickname == 'RQ'
		assertEquals(goalChangeMessages2[0].creationTime, YonaServer.now)
		goalChangeMessages2[0].message == "Want to become even more social :)"
		goalChangeMessages2[0]._links.edit
		goalChangeMessages2[1].change == 'GOAL_CHANGED'
		goalChangeMessages2[2].change == 'GOAL_ADDED'

		def richardMessagesResponse2 = appService.getMessages(richard)
		richardMessagesResponse2.responseData._embedded."yona:messages".findAll
				{ it."@type" == "GoalChangeMessage" }.size() == 0

		// Change 3
		when:
		BudgetGoal updatedGoal3 = BudgetGoal.createInstance(SOCIAL_ACT_CAT_URL, 240)
		def response3 = appService.updateGoal(richard, addedGoal.url, updatedGoal3, "Want to become extremely social :)")

		then:
		response3.status == 200
		response3.responseData.maxDurationMinutes == 240

		def responseGoalsAfterUpdate3 = appService.getGoals(richard)
		responseGoalsAfterUpdate3.status == 200
		responseGoalsAfterUpdate3.responseData._embedded."yona:goals".size() == 6
		findActiveGoal(responseGoalsAfterUpdate3, SOCIAL_ACT_CAT_URL).maxDurationMinutes == 240
		assertEquals(findActiveGoal(responseGoalsAfterUpdate, SOCIAL_ACT_CAT_URL).creationTime, YonaServer.now)

		def allSocialGoals3 = findGoalsIncludingHistoryItems(responseGoalsAfterUpdate3, SOCIAL_ACT_CAT_URL)
		allSocialGoals3.size() == 4
		def historyItem3 = allSocialGoals.find
		{ it.historyItem }
		historyItem3.maxDurationMinutes == 60

		def bobMessagesResponse3 = appService.getMessages(bob)
		def goalChangeMessages3 = bobMessagesResponse3.responseData._embedded."yona:messages".findAll
		{ it."@type" == "GoalChangeMessage" }
		goalChangeMessages3.size() == 4
		goalChangeMessages3[0].change == 'GOAL_CHANGED'
		goalChangeMessages3[0]._links.related.href == SOCIAL_ACT_CAT_URL
		goalChangeMessages3[0]._links?."yona:buddy"?.href == bob.buddies[0].url
		goalChangeMessages3[0]._links?."yona:user"?.href == richard.url
		goalChangeMessages3[0]._embedded?."yona:user" == null
		goalChangeMessages3[0].nickname == 'RQ'
		assertEquals(goalChangeMessages3[0].creationTime, YonaServer.now)
		goalChangeMessages3[0].message == "Want to become extremely social :)"
		goalChangeMessages3[0]._links.edit
		goalChangeMessages3[1].change == 'GOAL_CHANGED'
		goalChangeMessages3[2].change == 'GOAL_CHANGED'
		goalChangeMessages3[3].change == 'GOAL_ADDED'

		def richardMessagesResponse3 = appService.getMessages(richard)
		richardMessagesResponse3.responseData._embedded."yona:messages".findAll
				{ it."@type" == "GoalChangeMessage" }.size() == 0

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Try update type of goal'()
	{
		given:
		def richard = addRichard()
		Goal addedGoal = appService.addGoal(appService.&assertResponseStatusCreated, richard, BudgetGoal.createInstance(SOCIAL_ACT_CAT_URL, 60), "Going to monitor my social time!")
		TimeZoneGoal updatedGoal = TimeZoneGoal.createInstance(SOCIAL_ACT_CAT_URL, ["11:00-12:00"].toArray())

		when:
		def response = appService.updateGoal(richard, addedGoal.url, updatedGoal)

		then:
		response.status == 400
		response.responseData.code == "error.goal.cannot.change.type"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Try update activity category of goal'()
	{
		given:
		def richard = addRichard()
		Goal addedGoal = appService.addGoal(appService.&assertResponseStatusCreated, richard, BudgetGoal.createInstance(SOCIAL_ACT_CAT_URL, 60), "Going to monitor my social time!")
		BudgetGoal updatedGoal = BudgetGoal.createInstance(NEWS_ACT_CAT_URL, 60)

		when:
		def response = appService.updateGoal(richard, addedGoal.url, updatedGoal)

		then:
		response.status == 400
		response.responseData.code == "error.goal.cannot.change.activity.category"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Try invalid budget goal'()
	{
		given:
		def richard = addRichard()

		when:
		def response = appService.addGoal(richard, BudgetGoal.createInstance(NEWS_ACT_CAT_URL, -1))

		then:
		response.status == 400
		response.responseData.code == "error.goal.budget.invalid.max.duration.cannot.be.negative"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Try invalid time zone goal'()
	{
		given:
		def richard = addRichard()

		when:
		def noZoneResponse = appService.addGoal(richard, TimeZoneGoal.createInstance(SOCIAL_ACT_CAT_URL, [].toArray()))
		def invalidFormatResponse = appService.addGoal(richard, TimeZoneGoal.createInstance(SOCIAL_ACT_CAT_URL, ["31:00-12:00"].toArray()))
		def toNotBeyondFromResponse = appService.addGoal(richard, TimeZoneGoal.createInstance(SOCIAL_ACT_CAT_URL, ["12:00-12:00"].toArray()))
		def invalidFromHourResponse = appService.addGoal(richard, TimeZoneGoal.createInstance(SOCIAL_ACT_CAT_URL, ["25:00-12:00"].toArray()))
		def invalidToHourResponse = appService.addGoal(richard, TimeZoneGoal.createInstance(SOCIAL_ACT_CAT_URL, ["11:00-25:00"].toArray()))
		def fromNotQuarterHourResponse = appService.addGoal(richard, TimeZoneGoal.createInstance(SOCIAL_ACT_CAT_URL, ["11:01-12:00"].toArray()))
		def toNotQuarterHourResponse = appService.addGoal(richard, TimeZoneGoal.createInstance(SOCIAL_ACT_CAT_URL, ["11:00-12:16"].toArray()))
		def fromBeyond24Response = appService.addGoal(richard, TimeZoneGoal.createInstance(SOCIAL_ACT_CAT_URL, ["24:15-24:30"].toArray()))
		def toBeyond24Response = appService.addGoal(richard, TimeZoneGoal.createInstance(SOCIAL_ACT_CAT_URL, ["24:00-24:15"].toArray()))
		def fullDayResponse = appService.addGoal(richard, TimeZoneGoal.createInstance(SOCIAL_ACT_CAT_URL, ["00:00-24:00"].toArray()))

		then:
		noZoneResponse.status == 400
		noZoneResponse.responseData.code == "error.goal.time.zone.invalid.at.least.one.zone.required"
		invalidFormatResponse.status == 400
		invalidFormatResponse.responseData.code == "error.goal.time.zone.invalid.zone.format"
		toNotBeyondFromResponse.status == 400
		toNotBeyondFromResponse.responseData.code == "error.goal.time.zone.to.not.beyond.from"
		invalidFromHourResponse.status == 400
		invalidFromHourResponse.responseData.code == "error.goal.time.zone.not.a.valid.hour"
		invalidToHourResponse.status == 400
		invalidToHourResponse.responseData.code == "error.goal.time.zone.not.a.valid.hour"
		fromNotQuarterHourResponse.status == 400
		fromNotQuarterHourResponse.responseData.code == "error.goal.time.zone.not.a.quarter.hour"
		toNotQuarterHourResponse.status == 400
		toNotQuarterHourResponse.responseData.code == "error.goal.time.zone.not.a.quarter.hour"
		fromBeyond24Response.status == 400
		fromBeyond24Response.responseData.code == "error.goal.time.zone.beyond.twenty.four"
		toBeyond24Response.status == 400
		toBeyond24Response.responseData.code == "error.goal.time.zone.beyond.twenty.four"
		fullDayResponse.status == 201

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Try setting update time before creation time'()
	{
		given:
		def richard = addRichard()
		BudgetGoal goal = addBudgetGoal(richard, SOCIAL_ACT_CAT_URL, 180, "W-1 Thu 18:00")

		when:
		ZonedDateTime updateTime = YonaServer.relativeDateTimeStringToZonedDateTime("W-1 Thu 17:59")
		BudgetGoal updatedGoal = BudgetGoal.createInstance(goal, updateTime, 10)
		def response = appService.updateGoal(richard, goal.url, updatedGoal)

		then:
		response.status == 400
		response.responseData.code == "error.goal.update.time.before.original"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Delete goal'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		def richard = richardAndBob.richard
		def bob = richardAndBob.bob
		def addedGoal = appService.addGoal(appService.&assertResponseStatusCreated, richard, BudgetGoal.createInstance(SOCIAL_ACT_CAT_URL, 60))

		when:
		def response = appService.removeGoal(richard, addedGoal, "Don't want to monitor my social time anymore")

		then:
		response.status == 200

		def responseGoalsAfterDelete = appService.getGoals(richard)
		responseGoalsAfterDelete.status == 200
		responseGoalsAfterDelete.responseData._embedded."yona:goals".size() == 2

		def bobMessagesResponse = appService.getMessages(bob)
		def goalChangeMessages = bobMessagesResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalChangeMessage"}
		goalChangeMessages.size() == 2
		goalChangeMessages[0].change == 'GOAL_DELETED'
		goalChangeMessages[0]._links.related.href == SOCIAL_ACT_CAT_URL
		goalChangeMessages[0]._links?."yona:buddy"?.href == bob.buddies[0].url
		goalChangeMessages[0]._links?."yona:user"?.href == richard.url
		goalChangeMessages[0]._embedded?."yona:user" == null
		goalChangeMessages[0].nickname == 'RQ'
		assertEquals(goalChangeMessages[0].creationTime, YonaServer.now)
		goalChangeMessages[0].message == "Don't want to monitor my social time anymore"
		goalChangeMessages[0]._links.edit
		goalChangeMessages[1].change == 'GOAL_ADDED'

		def richardMessagesResponse = appService.getMessages(richard)
		richardMessagesResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalChangeMessage"}.size() == 0

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Goal conflict messages are removed when goal is removed'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob
		ZonedDateTime now = YonaServer.now
		def postToAeResponse = analysisService.postToAnalysisEngine(richard, ["news/media"], "http://www.refdag.nl")
		postToAeResponse.status == 200
		def getMessagesRichardBeforeGoalDeleteResponse = appService.getMessages(richard)
		getMessagesRichardBeforeGoalDeleteResponse.status == 200
		getMessagesRichardBeforeGoalDeleteResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage" }.size() == 1
		def getMessagesBobBeforeGoalDeleteResponse = appService.getMessages(bob)
		getMessagesBobBeforeGoalDeleteResponse.status == 200
		getMessagesBobBeforeGoalDeleteResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}.size() == 1

		Goal newsGoal = richard.findActiveGoal(NEWS_ACT_CAT_URL)

		when:
		def response = appService.removeGoal(richard, newsGoal, "Don't want to monitor my social time anymore")

		then:
		response.status == 200

		def getMessagesRichardAfterGoalDeleteResponse = appService.getMessages(richard)
		getMessagesRichardAfterGoalDeleteResponse.status == 200
		getMessagesRichardAfterGoalDeleteResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage" }.size() == 0
		def getMessagesBobAfterGoalDeleteResponse = appService.getMessages(bob)
		getMessagesBobAfterGoalDeleteResponse.status == 200
		getMessagesBobAfterGoalDeleteResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}.size() == 0

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Validation: Try to remove mandatory goal'()
	{
		given:
		User richard = addRichard()
		def Goal gamblingGoal = richard.findActiveGoal(GAMBLING_ACT_CAT_URL)

		when:
		def response = appService.deleteResourceWithPassword(gamblingGoal.url, richard.password)

		then:
		response.status == 400
		def responseGoalsAfterDelete = appService.getGoals(richard)
		responseGoalsAfterDelete.status == 200
		responseGoalsAfterDelete.responseData._embedded."yona:goals".size() == 2

		cleanup:
		appService.deleteUser(richard)
	}

	def filterGoals(def response, def activityCategoryUrl)
	{
		response.responseData._embedded."yona:goals".findAll{ it._links."yona:activityCategory".href == activityCategoryUrl }
	}

	def postFacebookActivityPastHour(User user)
	{
		def startTime = YonaServer.now.minus(Duration.ofHours(1))
		def endTime = YonaServer.now
		appService.postAppActivityToAnalysisEngine(user, AppActivity.singleActivity("Facebook", startTime, endTime))
	}
}
