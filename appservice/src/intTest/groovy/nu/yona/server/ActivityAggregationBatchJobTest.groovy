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

	def 'Days and weeks in the past are aggregated once'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob
		// Trigger aggregation for any already existing activities
		assert batchService.triggerActivityAggregationBatchJob().status == 200

		setGoalCreationTime(richard, NEWS_ACT_CAT_URL, "W-1 Mon 02:18")
		reportAppActivity(richard, "NU.nl", "W-1 Mon 03:15", "W-1 Mon 03:35")
		addTimeZoneGoal(richard, SOCIAL_ACT_CAT_URL, ["11:00-12:00"], "W-1 Wed 13:55")
		reportNetworkActivity(richard, ["social"], "http://www.facebook.com", "W-1 Wed 15:00")
		reportNetworkActivity(richard, ["social"], "http://www.facebook.com", "W-1 Thu 11:30")

		richard = appService.reloadUser(richard)
		Goal budgetGoalNewsRichard = richard.findActiveGoal(NEWS_ACT_CAT_URL)
		Goal timeZoneGoalSocialRichard = richard.findActiveGoal(SOCIAL_ACT_CAT_URL)
		def expectedValuesRichardLastWeek = [
			"Mon" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: false, minutesBeyondGoal: 20, spread: [13 : 15, 14 : 5]]]],
			"Tue" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Wed" : [
				[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]],
				[goal:timeZoneGoalSocialRichard, data: [goalAccomplished: false, minutesBeyondGoal: 1, spread: [60 : 1]]]
			],
			"Thu" : [
				[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]],
				[goal:timeZoneGoalSocialRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [46 : 1]]]
			],
			"Fri" : [
				[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]],
				[goal:timeZoneGoalSocialRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [ : ]]]
			],
			"Sat" : [
				[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]],
				[goal:timeZoneGoalSocialRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [ : ]]]
			]]
		assertActivityValues(richard, 1, expectedValuesRichardLastWeek, 2)

		when:
		def response = batchService.triggerActivityAggregationBatchJob()

		then:
		response.status == 200
		response.responseData.writeCountPerStep?.aggregateWeekActivities == 2
		response.responseData.writeCountPerStep?.aggregateDayActivities == 10
		assertActivityValues(richard, 1, expectedValuesRichardLastWeek, 2)

		def secondAggregationResponse = batchService.triggerActivityAggregationBatchJob()
		secondAggregationResponse.status == 200
		secondAggregationResponse.responseData.writeCountPerStep?.aggregateWeekActivities == 0
		// Do not assert aggregateDayActivities; inactivity for past days of current week is suddenly added on second retrieval of week overview, one per loaded goal.

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Aggregation is reset and repeated when posting new app activity within combine interval'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob
		// Trigger aggregation for any already existing activities
		assert batchService.triggerActivityAggregationBatchJob().status == 200

		setGoalCreationTime(richard, NEWS_ACT_CAT_URL, "W-1 Mon 02:18")
		reportAppActivity(richard, "NU.nl", "W-1 Mon 23:00", "W-1 Mon 23:49")
		def firstAggregationResponse = batchService.triggerActivityAggregationBatchJob()
		assert firstAggregationResponse.status == 200
		assert firstAggregationResponse.responseData.writeCountPerStep?.aggregateWeekActivities == 1
		assert firstAggregationResponse.responseData.writeCountPerStep?.aggregateDayActivities == 1

		richard = appService.reloadUser(richard)
		Goal budgetGoalNewsRichard = richard.findActiveGoal(NEWS_ACT_CAT_URL)
		def expectedValuesFirstAggregation = [
			"Mon" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: false, minutesBeyondGoal: 49, spread: [92:15, 93:15, 94:15, 95:4]]]],
			"Tue" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Wed" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Thu" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Fri" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Sat" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]]]
		assertActivityValues(richard, 1, expectedValuesFirstAggregation, 2)

		when:
		reportAppActivity(richard, "NU.nl", "W-1 Mon 23:50", "W-1 Tue 00:05")

		then:
		def expectedValuesSecondAggregation = [
			"Mon" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: false, minutesBeyondGoal: 59, spread: [92:15, 93:15, 94:15, 95:14]]]],
			"Tue" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: false, minutesBeyondGoal: 5, spread: [0:5]]]],
			"Wed" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Thu" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Fri" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Sat" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]]]
		assertActivityValues(richard, 1, expectedValuesSecondAggregation, 2)

		def response = batchService.triggerActivityAggregationBatchJob()
		response.status == 200
		response.responseData.writeCountPerStep?.aggregateWeekActivities == 1
		response.responseData.writeCountPerStep?.aggregateDayActivities == 1 + 5 + YonaServer.getCurrentDayOfWeek() //days are initialized with inactivity, side effect of retrieving and asserting activity
		assertActivityValues(richard, 1, expectedValuesSecondAggregation, 2)

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Aggregation is reset and repeated when posting new app activity outside combine interval'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob
		// Trigger aggregation for any already existing activities
		assert batchService.triggerActivityAggregationBatchJob().status == 200

		setGoalCreationTime(richard, NEWS_ACT_CAT_URL, "W-1 Mon 02:18")
		reportAppActivity(richard, "NU.nl", "W-1 Mon 23:00", "W-1 Mon 23:05")
		def firstAggregationResponse = batchService.triggerActivityAggregationBatchJob()
		assert firstAggregationResponse.status == 200
		assert firstAggregationResponse.responseData.writeCountPerStep?.aggregateWeekActivities == 1
		assert firstAggregationResponse.responseData.writeCountPerStep?.aggregateDayActivities == 1

		richard = appService.reloadUser(richard)
		Goal budgetGoalNewsRichard = richard.findActiveGoal(NEWS_ACT_CAT_URL)
		def expectedValuesFirstAggregation = [
			"Mon" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: false, minutesBeyondGoal: 5, spread: [92:5]]]],
			"Tue" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Wed" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Thu" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Fri" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Sat" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]]]
		assertActivityValues(richard, 1, expectedValuesFirstAggregation, 2)

		when:
		reportAppActivity(richard, "NU.nl", "W-1 Mon 23:50", "W-1 Mon 23:59")

		then:
		def expectedValuesSecondAggregation = [
			"Mon" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: false, minutesBeyondGoal: 14, spread: [92:5, 95:9]]]],
			"Tue" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Wed" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Thu" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Fri" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Sat" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]]]
		assertActivityValues(richard, 1, expectedValuesSecondAggregation, 2)

		def response = batchService.triggerActivityAggregationBatchJob()
		response.status == 200
		response.responseData.writeCountPerStep?.aggregateWeekActivities == 1
		response.responseData.writeCountPerStep?.aggregateDayActivities == 1 + 5 + YonaServer.getCurrentDayOfWeek() //days are initialized with inactivity, side effect of retrieving and asserting activity
		assertActivityValues(richard, 1, expectedValuesSecondAggregation, 2)

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}
}
