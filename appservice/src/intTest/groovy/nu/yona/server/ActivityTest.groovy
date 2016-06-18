/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import groovy.json.*

import java.time.ZonedDateTime

import nu.yona.server.test.AppActivity
import nu.yona.server.test.Goal
import nu.yona.server.test.User
import spock.lang.IgnoreRest

class ActivityTest extends AbstractAppServiceIntegrationTest
{
	def 'Page through multiple weeks'()
	{
		given:
		def richard = addRichard()

		setGoalCreationTime(richard, NEWS_ACT_CAT_URL, "W-4 Mon 02:18")

		richard = appService.getUser(appService.&assertUserGetResponseDetailsWithPrivateData, richard.url, true, richard.password)
		Goal budgetGoalNews = richard.findActiveGoal(NEWS_ACT_CAT_URL)

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

	@IgnoreRest
	def 'Retrieve activity report of previous week'()
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
		Goal budgetGoalNews = richard.findActiveGoal(NEWS_ACT_CAT_URL)
		Goal timeZoneGoalSocial = richard.findActiveGoal(SOCIAL_ACT_CAT_URL)

		def expectedValuesLastWeek = [
			"Mon" : [[goal:budgetGoalNews, data: [goalAccomplished: false, minutesBeyondGoal: 20, spread: [13 : 15, 14 : 5]]]],
			"Tue" : [[goal:budgetGoalNews, data: [goalAccomplished: false, minutesBeyondGoal: 25, spread: [35 : 15, 36 : 10]]]],
			"Wed" : [[goal:budgetGoalNews, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalSocial, data: [goalAccomplished: false, minutesBeyondGoal: 1, spread: [68 : 1]]]],
			"Thu" : [[goal:budgetGoalNews, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalSocial, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [48 : 1]]]],
			"Fri" : [[goal:budgetGoalNews, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalSocial, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [ : ]]]],
			"Sat" : [[goal:budgetGoalNews, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalSocial, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [ : ]]]]]

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
		//get all days at once (max 2 weeks) to make assertion easy
		def responseDayOverviewsWithBuddies = appService.getDayActivityOverviewsWithBuddies(richard, ["size": 14])

		then:
		assertWeekOverviewBasics(responseWeekOverviews, [3, 2], expectedTotalWeeks)
		def weekOverviewLastWeek = responseWeekOverviews.responseData._embedded."yona:weekActivityOverviews"[1]
		assertNumberOfReportedDaysForGoalInWeekOverview(weekOverviewLastWeek, budgetGoalNews, 6)
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, budgetGoalNews, expectedValuesLastWeek, "Mon")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, budgetGoalNews, expectedValuesLastWeek, "Tue")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, budgetGoalNews, expectedValuesLastWeek, "Wed")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, budgetGoalNews, expectedValuesLastWeek, "Thu")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, budgetGoalNews, expectedValuesLastWeek, "Fri")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, budgetGoalNews, expectedValuesLastWeek, "Sat")

		assertWeekDetailForGoal(richard, weekOverviewLastWeek, budgetGoalNews, expectedValuesLastWeek)

		assertNumberOfReportedDaysForGoalInWeekOverview(weekOverviewLastWeek, timeZoneGoalSocial, 4)
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, timeZoneGoalSocial, expectedValuesLastWeek, "Wed")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, timeZoneGoalSocial, expectedValuesLastWeek, "Thu")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, timeZoneGoalSocial, expectedValuesLastWeek, "Fri")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, timeZoneGoalSocial, expectedValuesLastWeek, "Sat")

		assertDayOverviewBasics(responseDayOverviewsAll, expectedTotalDays, expectedTotalDays, 14)
		assertDayOverviewBasics(responseDayOverviewsPage1, 3, expectedTotalDays)
		assertDayOverviewBasics(responseDayOverviewsPage2, 3, expectedTotalDays)
		assertDayOverviewForBudgetGoal(responseDayOverviewsAll, budgetGoalNews, expectedValuesLastWeek, 1, "Mon")
		assertDayOverviewForBudgetGoal(responseDayOverviewsAll, budgetGoalNews, expectedValuesLastWeek, 1, "Tue")
		assertDayOverviewForBudgetGoal(responseDayOverviewsAll, budgetGoalNews, expectedValuesLastWeek, 1, "Wed")
		assertDayOverviewForBudgetGoal(responseDayOverviewsAll, budgetGoalNews, expectedValuesLastWeek, 1, "Thu")
		assertDayOverviewForBudgetGoal(responseDayOverviewsAll, budgetGoalNews, expectedValuesLastWeek, 1, "Fri")
		assertDayOverviewForBudgetGoal(responseDayOverviewsAll, budgetGoalNews, expectedValuesLastWeek, 1, "Sat")

		assertDayOverviewForTimeZoneGoal(responseDayOverviewsAll, timeZoneGoalSocial, expectedValuesLastWeek, 1, "Wed")
		assertDayOverviewForTimeZoneGoal(responseDayOverviewsAll, timeZoneGoalSocial, expectedValuesLastWeek, 1, "Thu")
		assertDayOverviewForTimeZoneGoal(responseDayOverviewsAll, timeZoneGoalSocial, expectedValuesLastWeek, 1, "Fri")
		assertDayOverviewForTimeZoneGoal(responseDayOverviewsAll, timeZoneGoalSocial, expectedValuesLastWeek, 1, "Sat")

		assertDayDetail(richard, responseDayOverviewsAll, budgetGoalNews, expectedValuesLastWeek, 1, "Mon")
		assertDayDetail(richard, responseDayOverviewsAll, budgetGoalNews, expectedValuesLastWeek, 1, "Tue")
		assertDayDetail(richard, responseDayOverviewsAll, budgetGoalNews, expectedValuesLastWeek, 1, "Wed")
		assertDayDetail(richard, responseDayOverviewsAll, budgetGoalNews, expectedValuesLastWeek, 1, "Thu")
		assertDayDetail(richard, responseDayOverviewsAll, budgetGoalNews, expectedValuesLastWeek, 1, "Fri")
		assertDayDetail(richard, responseDayOverviewsAll, budgetGoalNews, expectedValuesLastWeek, 1, "Sat")

		assertDayDetail(richard, responseDayOverviewsAll, timeZoneGoalSocial, expectedValuesLastWeek, 1, "Wed")
		assertDayDetail(richard, responseDayOverviewsAll, timeZoneGoalSocial, expectedValuesLastWeek, 1, "Thu")
		assertDayDetail(richard, responseDayOverviewsAll, timeZoneGoalSocial, expectedValuesLastWeek, 1, "Fri")
		assertDayDetail(richard, responseDayOverviewsAll, timeZoneGoalSocial, expectedValuesLastWeek, 1, "Sat")

		assertDayOverviewWithBuddiesBasics(responseDayOverviewsWithBuddies, expectedTotalDays, expectedTotalDays, 14)
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, NEWS_ACT_CAT_URL, expectedValuesLastWeek, 1, "Mon")
		assertDayOverviewWithBuddiesForBudgetGoal(responseDayOverviewsWithBuddies, richard, budgetGoalNews, expectedValuesLastWeek, 1, "Mon")
		assertDayOverviewWithBuddiesForBudgetGoal(responseDayOverviewsWithBuddies, richard, budgetGoalNews, expectedValuesLastWeek, 1, "Tue")
		assertDayOverviewWithBuddiesForBudgetGoal(responseDayOverviewsWithBuddies, richard, budgetGoalNews, expectedValuesLastWeek, 1, "Wed")
		assertDayOverviewWithBuddiesForBudgetGoal(responseDayOverviewsWithBuddies, richard, budgetGoalNews, expectedValuesLastWeek, 1, "Thu")
		assertDayOverviewWithBuddiesForBudgetGoal(responseDayOverviewsWithBuddies, richard, budgetGoalNews, expectedValuesLastWeek, 1, "Fri")
		assertDayOverviewWithBuddiesForBudgetGoal(responseDayOverviewsWithBuddies, richard, budgetGoalNews, expectedValuesLastWeek, 1, "Sat")

		assertDayOverviewWithBuddiesForTimeZoneGoal(responseDayOverviewsWithBuddies, richard, timeZoneGoalSocial, expectedValuesLastWeek, 1, "Wed")
		assertDayOverviewWithBuddiesForTimeZoneGoal(responseDayOverviewsWithBuddies, richard, timeZoneGoalSocial, expectedValuesLastWeek, 1, "Thu")
		assertDayOverviewWithBuddiesForTimeZoneGoal(responseDayOverviewsWithBuddies, richard, timeZoneGoalSocial, expectedValuesLastWeek, 1, "Fri")
		assertDayOverviewWithBuddiesForTimeZoneGoal(responseDayOverviewsWithBuddies, richard, timeZoneGoalSocial, expectedValuesLastWeek, 1, "Sat")
		/*
		 cleanup:
		 TODO
		 appService.deleteUser(richard)
		 appService.deleteUser(bob)
		 */
	}

	def 'Add activity after retrieving the report'()
	{
		given:
		User richard = addRichard()
		def ZonedDateTime now = YonaServer.now
		Goal budgetGoalGambling = richard.findActiveGoal(GAMBLING_ACT_CAT_URL)
		Goal budgetGoalNews = richard.findActiveGoal(NEWS_ACT_CAT_URL)
		def currentShortDay = getCurrentShortDay(now)
		def initialExpectedValues = [
			(currentShortDay) : [[goal:budgetGoalNews, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:budgetGoalGambling, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [ : ]]]]]
		def initialResponseWeekOverviews = appService.getWeekActivityOverviews(richard)
		def initialResponseDayOverviews = appService.getDayActivityOverviews(richard)
		assert initialResponseWeekOverviews.status == 200
		def initialCurrentWeekOverview = initialResponseWeekOverviews.responseData._embedded."yona:weekActivityOverviews"[0]
		assertDayInWeekOverviewForGoal(initialCurrentWeekOverview, budgetGoalNews, initialExpectedValues, currentShortDay)
		assertWeekDetailForGoal(richard, initialCurrentWeekOverview, budgetGoalNews, initialExpectedValues)
		assertDayOverviewForBudgetGoal(initialResponseDayOverviews, budgetGoalNews, initialExpectedValues, 0, currentShortDay)
		assertDayDetail(richard, initialResponseDayOverviews, budgetGoalNews, initialExpectedValues, 0, currentShortDay)

		when:
		reportAppActivities(richard, AppActivity.singleActivity("NU.nl", now, now))

		then:
		def expectedValuesAfterActivity = [
			(currentShortDay) : [[goal:budgetGoalNews, data: [goalAccomplished: false, minutesBeyondGoal: 1, spread: [(getCurrentSpreadCell(now)) : 1]]], [goal:budgetGoalGambling, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [ : ]]]]]
		def responseWeekOverviewsAfterActivity = appService.getWeekActivityOverviews(richard)
		def currentWeekOverviewAfterActivity = responseWeekOverviewsAfterActivity.responseData._embedded."yona:weekActivityOverviews"[0]
		assertDayInWeekOverviewForGoal(currentWeekOverviewAfterActivity, budgetGoalNews, expectedValuesAfterActivity, currentShortDay)
		assertWeekDetailForGoal(richard, currentWeekOverviewAfterActivity, budgetGoalNews, expectedValuesAfterActivity)

		def responseDayOverviewsAfterActivity = appService.getDayActivityOverviews(richard)
		assertDayOverviewForBudgetGoal(responseDayOverviewsAfterActivity, budgetGoalNews, expectedValuesAfterActivity, 0, currentShortDay)
		assertDayDetail(richard, responseDayOverviewsAfterActivity, budgetGoalNews, expectedValuesAfterActivity, 0, currentShortDay)

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Remove goal after adding activities'()
	{
		given:
		User richard = addRichard()
		setGoalCreationTime(richard, NEWS_ACT_CAT_URL, "W-1 Mon 02:18")
		reportAppActivity(richard, "NU.nl", "W-1 Mon 03:15", "W-1 Mon 03:35")
		reportAppActivities(richard, [createAppActivity("NU.nl", "W-1 Tue 08:45", "W-1 Tue 09:10"), createAppActivity("Facebook", "W-1 Tue 09:35", "W-1 Mon 10:10")])
		addTimeZoneGoal(richard, SOCIAL_ACT_CAT_URL, ["11:00-12:00"], "W-1 Wed 13:55")
		reportNetworkActivity(richard, ["social"], "http://www.facebook.com", "W-1 Wed 15:00")
		reportNetworkActivity(richard, ["social"], "http://www.facebook.com", "W-1 Thu 11:30")

		richard = appService.getUser(appService.&assertUserGetResponseDetailsWithPrivateData, richard.url, true, richard.password)
		Goal budgetGoalNews = richard.findActiveGoal(NEWS_ACT_CAT_URL)
		Goal timeZoneGoalSocial = richard.findActiveGoal(SOCIAL_ACT_CAT_URL)

		def expectedValuesLastWeekBeforeDelete = [
			"Mon" : [[goal:budgetGoalNews, data: [goalAccomplished: false, minutesBeyondGoal: 20, spread: [13 : 15, 14 : 5]]]],
			"Tue" : [[goal:budgetGoalNews, data: [goalAccomplished: false, minutesBeyondGoal: 25, spread: [35 : 15, 36 : 10]]]],
			"Wed" : [[goal:budgetGoalNews, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalSocial, data: [goalAccomplished: false, minutesBeyondGoal: 1, spread: [68 : 1]]]],
			"Thu" : [[goal:budgetGoalNews, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalSocial, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [48 : 1]]]],
			"Fri" : [[goal:budgetGoalNews, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalSocial, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [ : ]]]],
			"Sat" : [[goal:budgetGoalNews, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalSocial, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [ : ]]]]]
		def expectedGoalsPerWeekBeforeDelete = [3, 2]

		def currentDayOfWeek = YonaServer.getCurrentDayOfWeek()
		def expectedTotalDaysBeforeDelete = 6 + currentDayOfWeek + 1
		def expectedTotalWeeksBeforeDelete = 2
		def responseWeekOverviewsBeforeDelete = appService.getWeekActivityOverviews(richard)
		//get all days at once (max 2 weeks) to make assertion easy
		def responseDayOverviewsAllBeforeDelete = appService.getDayActivityOverviews(richard, ["size": 14])

		assertWeekOverviewBasics(responseWeekOverviewsBeforeDelete, expectedGoalsPerWeekBeforeDelete, expectedTotalWeeksBeforeDelete)
		def weekOverviewLastWeekBeforeDelete = responseWeekOverviewsBeforeDelete.responseData._embedded."yona:weekActivityOverviews"[1]
		assertNumberOfReportedDaysForGoalInWeekOverview(weekOverviewLastWeekBeforeDelete, budgetGoalNews, 6)
		assertDayInWeekOverviewForGoal(weekOverviewLastWeekBeforeDelete, budgetGoalNews, expectedValuesLastWeekBeforeDelete, "Mon")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeekBeforeDelete, budgetGoalNews, expectedValuesLastWeekBeforeDelete, "Tue")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeekBeforeDelete, budgetGoalNews, expectedValuesLastWeekBeforeDelete, "Wed")

		assertWeekDetailForGoal(richard, weekOverviewLastWeekBeforeDelete, budgetGoalNews, expectedValuesLastWeekBeforeDelete)

		assertDayOverviewBasics(responseDayOverviewsAllBeforeDelete, expectedTotalDaysBeforeDelete, expectedTotalDaysBeforeDelete, 14)
		assertDayOverviewForBudgetGoal(responseDayOverviewsAllBeforeDelete, budgetGoalNews, expectedValuesLastWeekBeforeDelete, 1, "Mon")
		assertDayOverviewForBudgetGoal(responseDayOverviewsAllBeforeDelete, budgetGoalNews, expectedValuesLastWeekBeforeDelete, 1, "Tue")
		assertDayOverviewForBudgetGoal(responseDayOverviewsAllBeforeDelete, budgetGoalNews, expectedValuesLastWeekBeforeDelete, 1, "Wed")

		assertDayDetail(richard, responseDayOverviewsAllBeforeDelete, budgetGoalNews, expectedValuesLastWeekBeforeDelete, 1, "Mon")
		assertDayDetail(richard, responseDayOverviewsAllBeforeDelete, budgetGoalNews, expectedValuesLastWeekBeforeDelete, 1, "Tue")
		assertDayDetail(richard, responseDayOverviewsAllBeforeDelete, budgetGoalNews, expectedValuesLastWeekBeforeDelete, 1, "Wed")

		assertNumberOfReportedDaysForGoalInWeekOverview(weekOverviewLastWeekBeforeDelete, timeZoneGoalSocial, 4)
		assertDayInWeekOverviewForGoal(weekOverviewLastWeekBeforeDelete, timeZoneGoalSocial, expectedValuesLastWeekBeforeDelete, "Wed")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeekBeforeDelete, timeZoneGoalSocial, expectedValuesLastWeekBeforeDelete, "Thu")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeekBeforeDelete, timeZoneGoalSocial, expectedValuesLastWeekBeforeDelete, "Fri")

		assertDayDetail(richard, responseDayOverviewsAllBeforeDelete, timeZoneGoalSocial, expectedValuesLastWeekBeforeDelete, 1, "Wed")
		assertDayDetail(richard, responseDayOverviewsAllBeforeDelete, timeZoneGoalSocial, expectedValuesLastWeekBeforeDelete, 1, "Thu")
		assertDayDetail(richard, responseDayOverviewsAllBeforeDelete, timeZoneGoalSocial, expectedValuesLastWeekBeforeDelete, 1, "Fri")

		when:
		def response = appService.removeGoal(richard, budgetGoalNews)

		then:
		def expectedTotalDaysAfterDelete = expectedTotalDaysBeforeDelete - 2
		def expectedTotalWeeksAfterDelete = expectedTotalWeeksBeforeDelete
		def expectedValuesLastWeekAfterDelete = [
			"Mon" : [[goal:budgetGoalNews, data: [goalAccomplished: false, minutesBeyondGoal: 20, spread: [13 : 15, 14 : 5]]]],
			"Tue" : [[goal:budgetGoalNews, data: [goalAccomplished: false, minutesBeyondGoal: 25, spread: [35 : 15, 36 : 10]]]],
			"Wed" : [[goal:budgetGoalNews, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalSocial, data: [goalAccomplished: false, minutesBeyondGoal: 1, spread: [68 : 1]]]],
			"Thu" : [[goal:budgetGoalNews, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalSocial, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [48 : 1]]]],
			"Fri" : [[goal:budgetGoalNews, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalSocial, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [ : ]]]],
			"Sat" : [[goal:budgetGoalNews, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalSocial, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [ : ]]]]]
		def expectedGoalsPerWeekAfterDelete = expectedGoalsPerWeekBeforeDelete.collect{it - 1}

		//get all days at once (max 2 weeks) to make assertion easy
		def responseWeekOverviewsAfterDelete = appService.getWeekActivityOverviews(richard)
		assertWeekOverviewBasics(responseWeekOverviewsAfterDelete, expectedGoalsPerWeekAfterDelete, expectedTotalWeeksAfterDelete)

		def weekOverviewLastWeekAfterDelete = responseWeekOverviewsAfterDelete.responseData._embedded."yona:weekActivityOverviews"[1]
		assertNumberOfReportedDaysForGoalInWeekOverview(weekOverviewLastWeekAfterDelete, timeZoneGoalSocial, 4)
		assertDayInWeekOverviewForGoal(weekOverviewLastWeekAfterDelete, timeZoneGoalSocial, expectedValuesLastWeekAfterDelete, "Wed")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeekAfterDelete, timeZoneGoalSocial, expectedValuesLastWeekAfterDelete, "Thu")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeekAfterDelete, timeZoneGoalSocial, expectedValuesLastWeekAfterDelete, "Fri")

		def responseDayOverviewsAllAfterDelete = appService.getDayActivityOverviews(richard, ["size": 14])
		assertDayOverviewBasics(responseDayOverviewsAllAfterDelete, expectedTotalDaysAfterDelete, expectedTotalDaysAfterDelete, 14)
		assertDayDetail(richard, responseDayOverviewsAllAfterDelete, timeZoneGoalSocial, expectedValuesLastWeekAfterDelete, 1, "Wed")
		assertDayDetail(richard, responseDayOverviewsAllAfterDelete, timeZoneGoalSocial, expectedValuesLastWeekAfterDelete, 1, "Thu")
		assertDayDetail(richard, responseDayOverviewsAllAfterDelete, timeZoneGoalSocial, expectedValuesLastWeekAfterDelete, 1, "Fri")

		// See whether we can still report activities the activity category of the deleted goal
		reportAppActivity(richard, "NU.nl", "W-1 Mon 03:15", "W-1 Mon 03:35")

		// Activity report should be identical
		def responseWeekOverviewsNewActivity = appService.getWeekActivityOverviews(richard)
		assertWeekOverviewBasics(responseWeekOverviewsNewActivity, expectedGoalsPerWeekAfterDelete, expectedTotalWeeksAfterDelete)

		def weekOverviewLastWeekAfterNewActivity = responseWeekOverviewsNewActivity.responseData._embedded."yona:weekActivityOverviews"[1]
		assertNumberOfReportedDaysForGoalInWeekOverview(weekOverviewLastWeekAfterNewActivity, timeZoneGoalSocial, 4)

		def responseDayOverviewsAllAfterNewActivity = appService.getDayActivityOverviews(richard, ["size": 14])
		assertDayOverviewBasics(responseDayOverviewsAllAfterNewActivity, expectedTotalDaysAfterDelete, expectedTotalDaysAfterDelete, 14)

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Add goals and fetch activities'()
	{
		given:
		User richard = addRichard()
		addTimeZoneGoal(richard, MULTIMEDIA_ACT_CAT_URL, ["11:00-12:00"])
		assert appService.getDayActivityOverviews(richard).status == 200
		assert appService.getWeekActivityOverviews(richard).status == 200

		when:
		addBudgetGoal(richard, COMMUNICATION_ACT_CAT_URL, 60)
		then:
		appService.getDayActivityOverviews(richard).status == 200
		appService.getWeekActivityOverviews(richard).status == 200

		cleanup:
		appService.deleteUser(richard)
	}
}