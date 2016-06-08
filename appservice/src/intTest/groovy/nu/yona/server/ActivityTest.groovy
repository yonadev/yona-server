/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import groovy.json.*
import nu.yona.server.test.User

class ActivityTest extends AbstractAppServiceIntegrationTest
{
	def 'Test pagination multiple weeks'()
	{
		given:
		def richard = addRichard()

		setGoalCreationTime(richard, NEWS_ACT_CAT_URL, "W-4 Mon 02:18")

		richard = appService.getUser(appService.&assertUserGetResponseDetailsWithPrivateData, richard.url, true, richard.password)
		def budgetGoalNewsUrl = richard.findActiveGoal(NEWS_ACT_CAT_URL).url

		def currentDayOfWeek = YonaServer.getCurrentDayOfWeek()
		def expectedTotalDays = 6 + 7*3 + currentDayOfWeek + 1
		def expectedTotalWeeks = 5

		when:
		//we can safely get two normal pages
		def responseWeekOverviewsPage1 = appService.getWeekActivityOverviews(richard)
		def responseWeekOverviewsPage2 = appService.getWeekActivityOverviews(richard, ["page": 1])
		def responseWeekOverviewsPage3 = appService.getWeekActivityOverviews(richard, ["page": 2])
		//we can safely get two normal pages
		def responseDayOverviewsPage1 = appService.getDayActivityOverviews(richard)
		def responseDayOverviewsPage2 = appService.getDayActivityOverviews(richard, ["page": 1])

		then:
		assertWeekOverviewBasics(responseWeekOverviewsPage1, [2, 1], expectedTotalWeeks)
		assertWeekOverviewBasics(responseWeekOverviewsPage2, [1, 1], expectedTotalWeeks)
		assertWeekOverviewBasics(responseWeekOverviewsPage3, [1], expectedTotalWeeks)

		assertDayOverviewBasics(responseDayOverviewsPage1, 3, expectedTotalDays)
		assertDayOverviewBasics(responseDayOverviewsPage2, 3, expectedTotalDays)

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Test activity in previous week'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob

		setGoalCreationTime(richard, NEWS_ACT_CAT_URL, "W-1 Mon 02:18")
		reportAppActivity(richard, "NU.nl", "W-1 Mon 03:15", "W-1 Mon 03:35")
		reportAppActivities(richard, [createAppActivity("NU.nl", "W-1 Tue 08:45", "W-1 Tue 09:10"), createAppActivity("Facebook", "W-1 Tue 09:35", "W-1 Mon 10:10")])
		addTimeZoneGoal(richard, SOCIAL_ACT_CAT_URL, ["11:00-12:00"], "W-1 Wed 13:55")
		reportNetworkActivity(richard, ["social"], "http://www.facebook.com", "W-1 Wed 15:00")
		reportNetworkActivity(richard, ["social"], "http://www.facebook.com", "W-1 Thu 11:30")

		richard = appService.getUser(appService.&assertUserGetResponseDetailsWithPrivateData, richard.url, true, richard.password)
		def budgetGoalNewsUrl = richard.findActiveGoal(NEWS_ACT_CAT_URL).url
		def timeZoneGoalSocialUrl = richard.findActiveGoal(SOCIAL_ACT_CAT_URL).url

		def expectedValuesLastWeek = [
			"Mon" : [[goalUrl:budgetGoalNewsUrl, data: [goalAccomplished: false, minutesBeyondGoal: 20, spread: [13 : 15, 14 : 5]]]],
			"Tue" : [[goalUrl:budgetGoalNewsUrl, data: [goalAccomplished: false, minutesBeyondGoal: 25, spread: [35 : 15, 36 : 10]]]],
			"Wed" : [
				[goalUrl:budgetGoalNewsUrl, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]],
				[goalUrl:timeZoneGoalSocialUrl, data: [goalAccomplished: false, minutesBeyondGoal: 1, spread: [68 : 1]]]
			],
			"Thu" : [
				[goalUrl:budgetGoalNewsUrl, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]],
				[goalUrl:timeZoneGoalSocialUrl, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [48 : 1]]]
			],
			"Fri" : [
				[goalUrl:budgetGoalNewsUrl, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]],
				[goalUrl:timeZoneGoalSocialUrl, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [ : ]]]
			],
			"Sat" : [
				[goalUrl:budgetGoalNewsUrl, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]],
				[goalUrl:timeZoneGoalSocialUrl, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [ : ]]]
			]]

		def currentDayOfWeek = YonaServer.getCurrentDayOfWeek()
		def expectedTotalDays = 6 + currentDayOfWeek + 1
		def expectedTotalWeeks = 2

		when:
		def responseWeekOverviews = appService.getWeekActivityOverviews(richard)
		//get all days at once (max 2 weeks) to make assertion easy
		def responseDayOverviewsAll = appService.getDayActivityOverviews(richard, ["size": 14])
		//the min amount of days is 1 this week + 6 previous week, so we can safely get two normal pages
		def responseDayOverviewsPage1 = appService.getDayActivityOverviews(richard)
		def responseDayOverviewsPage2 = appService.getDayActivityOverviews(richard, ["page": 1])

		then:
		assertWeekOverviewBasics(responseWeekOverviews, [3, 2], expectedTotalWeeks)
		def weekOverviewLastWeek = responseWeekOverviews.responseData._embedded."yona:weekActivityOverviews"[1]
		assertNumberOfReportedDaysForGoalInWeekOverview(weekOverviewLastWeek, budgetGoalNewsUrl, 6)
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, budgetGoalNewsUrl, expectedValuesLastWeek, "Mon")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, budgetGoalNewsUrl, expectedValuesLastWeek, "Tue")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, budgetGoalNewsUrl, expectedValuesLastWeek, "Wed")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, budgetGoalNewsUrl, expectedValuesLastWeek, "Thu")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, budgetGoalNewsUrl, expectedValuesLastWeek, "Fri")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, budgetGoalNewsUrl, expectedValuesLastWeek, "Sat")

		assertWeekDetailForGoal(richard, weekOverviewLastWeek, budgetGoalNewsUrl, expectedValuesLastWeek)

		assertNumberOfReportedDaysForGoalInWeekOverview(weekOverviewLastWeek, timeZoneGoalSocialUrl, 4)
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, timeZoneGoalSocialUrl, expectedValuesLastWeek, "Wed")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, timeZoneGoalSocialUrl, expectedValuesLastWeek, "Thu")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, timeZoneGoalSocialUrl, expectedValuesLastWeek, "Fri")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, timeZoneGoalSocialUrl, expectedValuesLastWeek, "Sat")

		assertDayOverviewBasics(responseDayOverviewsAll, expectedTotalDays, expectedTotalDays, 14)
		assertDayOverviewBasics(responseDayOverviewsPage1, 3, expectedTotalDays)
		assertDayOverviewBasics(responseDayOverviewsPage2, 3, expectedTotalDays)
		assertDayOverviewForBudgetGoal(responseDayOverviewsAll, budgetGoalNewsUrl, expectedValuesLastWeek, 1, "Mon")
		assertDayOverviewForBudgetGoal(responseDayOverviewsAll, budgetGoalNewsUrl, expectedValuesLastWeek, 1, "Tue")
		assertDayOverviewForBudgetGoal(responseDayOverviewsAll, budgetGoalNewsUrl, expectedValuesLastWeek, 1, "Wed")
		assertDayOverviewForBudgetGoal(responseDayOverviewsAll, budgetGoalNewsUrl, expectedValuesLastWeek, 1, "Thu")
		assertDayOverviewForBudgetGoal(responseDayOverviewsAll, budgetGoalNewsUrl, expectedValuesLastWeek, 1, "Fri")
		assertDayOverviewForBudgetGoal(responseDayOverviewsAll, budgetGoalNewsUrl, expectedValuesLastWeek, 1, "Sat")

		assertDayOverviewForTimeZoneGoal(responseDayOverviewsAll, timeZoneGoalSocialUrl, expectedValuesLastWeek, 1, "Wed")
		assertDayOverviewForTimeZoneGoal(responseDayOverviewsAll, timeZoneGoalSocialUrl, expectedValuesLastWeek, 1, "Thu")
		assertDayOverviewForTimeZoneGoal(responseDayOverviewsAll, timeZoneGoalSocialUrl, expectedValuesLastWeek, 1, "Fri")
		assertDayOverviewForTimeZoneGoal(responseDayOverviewsAll, timeZoneGoalSocialUrl, expectedValuesLastWeek, 1, "Sat")

		assertDayDetail(richard, responseDayOverviewsAll, budgetGoalNewsUrl, expectedValuesLastWeek, 1, "Mon")
		assertDayDetail(richard, responseDayOverviewsAll, budgetGoalNewsUrl, expectedValuesLastWeek, 1, "Tue")
		assertDayDetail(richard, responseDayOverviewsAll, budgetGoalNewsUrl, expectedValuesLastWeek, 1, "Wed")
		assertDayDetail(richard, responseDayOverviewsAll, budgetGoalNewsUrl, expectedValuesLastWeek, 1, "Thu")
		assertDayDetail(richard, responseDayOverviewsAll, budgetGoalNewsUrl, expectedValuesLastWeek, 1, "Fri")
		assertDayDetail(richard, responseDayOverviewsAll, budgetGoalNewsUrl, expectedValuesLastWeek, 1, "Sat")

		assertDayDetail(richard, responseDayOverviewsAll, timeZoneGoalSocialUrl, expectedValuesLastWeek, 1, "Wed")
		assertDayDetail(richard, responseDayOverviewsAll, timeZoneGoalSocialUrl, expectedValuesLastWeek, 1, "Thu")
		assertDayDetail(richard, responseDayOverviewsAll, timeZoneGoalSocialUrl, expectedValuesLastWeek, 1, "Fri")
		assertDayDetail(richard, responseDayOverviewsAll, timeZoneGoalSocialUrl, expectedValuesLastWeek, 1, "Sat")

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}
}
