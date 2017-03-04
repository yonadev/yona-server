/*******************************************************************************
 * Copyright (c) 2017 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import groovy.json.*
import nu.yona.server.test.BatchService
import nu.yona.server.test.Goal
import nu.yona.server.test.User
import spock.lang.Shared

class ActivityAggregationBatchJobTest extends AbstractAppServiceIntegrationTest
{
	@Shared
	def BatchService batchService = new BatchService()

	def 'Days and weeks in the past are aggregated'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob

		setGoalCreationTime(richard, NEWS_ACT_CAT_URL, "W-1 Mon 02:18")
		reportAppActivity(richard, "NU.nl", "W-1 Mon 03:15", "W-1 Mon 03:35")
		addTimeZoneGoal(richard, SOCIAL_ACT_CAT_URL, ["11:00-12:00"], "W-1 Wed 13:55")
		reportNetworkActivity(richard, ["social"], "http://www.facebook.com", "W-1 Wed 15:00")
		reportNetworkActivity(richard, ["social"], "http://www.facebook.com", "W-1 Thu 11:30")

		def expectedTotalDays = 1
		richard = appService.reloadUser(richard)
		Goal budgetGoalNewsRichard = richard.findActiveGoal(NEWS_ACT_CAT_URL)
		Goal timeZoneGoalSocialRichard = richard.findActiveGoal(SOCIAL_ACT_CAT_URL)
		def expectedValuesRichardLastWeek = [
			"Mon" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: false, minutesBeyondGoal: 20, spread: [13 : 15, 14 : 5]]]],
			"Tue" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: false, minutesBeyondGoal: 25, spread: [35 : 15, 36 : 10]]]],
			"Wed" : [
				[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]],
				[goal:timeZoneGoalSocialRichard, data: [goalAccomplished: false, minutesBeyondGoal: 1, spread: [68 : 1]]]
			],
			"Thu" : [
				[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]],
				[goal:timeZoneGoalSocialRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [48 : 1]]]
			],
			"Fri" : [
				[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]],
				[goal:timeZoneGoalSocialRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [ : ]]]
			],
			"Sat" : [
				[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]],
				[goal:timeZoneGoalSocialRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [ : ]]]
			]]
		assertDayOverviews(richard, 1, expectedValuesRichardLastWeek)
		assertWeekOverviews(richard, 1, expectedValuesRichardLastWeek, 2)

		when:
		def response = batchService.triggerAggregateActivities()

		then:
		response.status == 200
		assertDayOverviews(richard, 1, expectedValuesRichardLastWeek)
		assertWeekOverviews(richard, 1, expectedValuesRichardLastWeek, 2)

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Aggregation is reset and repeated when posting new app activity within combine interval'()
	{
		given:

		when:
		def response = batchService.triggerAggregateActivities()

		then:
		response.status == 200
	}

	def 'Aggregation is reset and repeated when posting new app activity after a day'()
	{
		given:

		when:
		def response = batchService.triggerAggregateActivities()

		then:
		response.status == 200
	}
}
