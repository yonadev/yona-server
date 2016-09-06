/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import groovy.json.*

import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.SignStyle
import java.time.temporal.IsoFields

import nu.yona.server.test.AppActivity
import nu.yona.server.test.Buddy
import nu.yona.server.test.BudgetGoal
import nu.yona.server.test.Goal
import nu.yona.server.test.TimeZoneGoal
import nu.yona.server.test.User

class ActivityTest extends AbstractAppServiceIntegrationTest
{
	def 'Retrieve activity reports without activity'()
	{
		given:
		def richard = addRichard()
		Goal goal = richard.findActiveGoal(GAMBLING_ACT_CAT_URL)

		when:
		def responseDayOverviews = appService.getDayActivityOverviews(richard)
		def responseWeekOverviews = appService.getWeekActivityOverviews(richard)

		then:
		assertDayOverviewBasics(responseDayOverviews, 1, 1)
		assertWeekOverviewBasics(responseWeekOverviews, [2], 1)

		def weekActivityForGoal = responseWeekOverviews.responseData._embedded."yona:weekActivityOverviews"[0].weekActivities.find{ it._links."yona:goal".href == goal.url}
		assert weekActivityForGoal?._links?."yona:weekDetails"?.href
		def weekActivityDetailUrl = weekActivityForGoal?._links?."yona:weekDetails"?.href
		def response = appService.getResourceWithPassword(weekActivityDetailUrl, richard.password)
		assert response.status == 200

		def dayActivityOverview = responseDayOverviews.responseData._embedded."yona:dayActivityOverviews"[0]
		def dayActivityForGoal = dayActivityOverview.dayActivities.find{ it._links."yona:goal".href == goal.url}
		assert dayActivityForGoal?._links?."yona:dayDetails"?.href
		def dayActivityDetailUrl =  dayActivityForGoal?._links?."yona:dayDetails"?.href
		def responseDayDetail = appService.getResourceWithPassword(dayActivityDetailUrl, richard.password)
		assert responseDayDetail.status == 200

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Page through multiple weeks'()
	{
		given:
		def richard = addRichard()

		setGoalCreationTime(richard, NEWS_ACT_CAT_URL, "W-4 Mon 02:18")

		richard = appService.reloadUser(richard)
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
		def responseDayOverviewsAllOnOnePage = appService.getDayActivityOverviews(richard, ["size": expectedTotalDays])

		then:
		assertWeekOverviewBasics(responseWeekOverviewsPage1, [2, 1], expectedTotalWeeks)
		assertWeekOverviewBasics(responseWeekOverviewsPage2, [1, 1], expectedTotalWeeks)
		assertWeekOverviewBasics(responseWeekOverviewsPage3, [1], expectedTotalWeeks)

		def week5ForGoal = responseWeekOverviewsPage3.responseData._embedded."yona:weekActivityOverviews"[0].weekActivities.find{ it._links."yona:goal".href == budgetGoalNews.url}
		def week4ForGoal = responseWeekOverviewsPage2.responseData._embedded."yona:weekActivityOverviews"[1].weekActivities.find{ it._links."yona:goal".href == budgetGoalNews.url}
		def week3ForGoal = responseWeekOverviewsPage2.responseData._embedded."yona:weekActivityOverviews"[0].weekActivities.find{ it._links."yona:goal".href == budgetGoalNews.url}
		def week2ForGoal = responseWeekOverviewsPage1.responseData._embedded."yona:weekActivityOverviews"[1].weekActivities.find{ it._links."yona:goal".href == budgetGoalNews.url}
		def week1ForGoal = responseWeekOverviewsPage1.responseData._embedded."yona:weekActivityOverviews"[0].weekActivities.find{ it._links."yona:goal".href == budgetGoalNews.url}
		assertWeekDetailPrevNextLinks(richard, week5ForGoal, null, week4ForGoal)
		assertWeekDetailPrevNextLinks(richard, week4ForGoal, week5ForGoal, week3ForGoal)
		assertWeekDetailPrevNextLinks(richard, week1ForGoal, week2ForGoal, null)

		assertDayOverviewBasics(responseDayOverviewsPage1, 3, expectedTotalDays)
		assertDayOverviewBasics(responseDayOverviewsPage2, 3, expectedTotalDays)

		def day1ForGoal = responseDayOverviewsPage1.responseData._embedded."yona:dayActivityOverviews"[0].dayActivities.find{ it._links."yona:goal".href == budgetGoalNews.url}
		def day2ForGoal = responseDayOverviewsPage1.responseData._embedded."yona:dayActivityOverviews"[1].dayActivities.find{ it._links."yona:goal".href == budgetGoalNews.url}
		def day3ForGoal = responseDayOverviewsPage1.responseData._embedded."yona:dayActivityOverviews"[2].dayActivities.find{ it._links."yona:goal".href == budgetGoalNews.url}
		def secondToLastDayForGoal = responseDayOverviewsAllOnOnePage.responseData._embedded."yona:dayActivityOverviews"[expectedTotalDays - 2].dayActivities.find{ it._links."yona:goal".href == budgetGoalNews.url}
		def lastDayForGoal = responseDayOverviewsAllOnOnePage.responseData._embedded."yona:dayActivityOverviews"[expectedTotalDays - 1].dayActivities.find{ it._links."yona:goal".href == budgetGoalNews.url}
		assertDayDetailPrevNextLinks(richard, lastDayForGoal, null, secondToLastDayForGoal)
		assertDayDetailPrevNextLinks(richard, day2ForGoal, day3ForGoal, day1ForGoal)
		assertDayDetailPrevNextLinks(richard, day1ForGoal, day2ForGoal, null)

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Retrieve activity report of previous week'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob

		setGoalCreationTime(richard, NEWS_ACT_CAT_URL, "W-1 Mon 02:18")
		reportAppActivity(richard, "NU.nl", "W-1 Mon 03:15", "W-1 Mon 03:35")
		reportAppActivities(richard, [createAppActivity("NU.nl", "W-1 Tue 08:45", "W-1 Tue 09:10"), createAppActivity("Facebook", "W-1 Tue 09:35", "W-1 Tue 10:10")])
		addTimeZoneGoal(richard, SOCIAL_ACT_CAT_URL, ["11:00-12:00"], "W-1 Wed 13:55")
		reportNetworkActivity(richard, ["social"], "http://www.facebook.com", "W-1 Wed 15:00")
		reportNetworkActivity(richard, ["social"], "http://www.facebook.com", "W-1 Thu 11:30")

		addBudgetGoal(bob, SOCIAL_ACT_CAT_URL, 180, "W-1 Thu 18:00")
		reportAppActivity(bob, "Facebook", "W-1 Thu 20:00", "W-1 Thu 20:35")

		addTimeZoneGoal(bob, MULTIMEDIA_ACT_CAT_URL, ["20:00-22:00"], "W-1 Fri 14:00")
		reportNetworkActivity(bob, ["YouTube"], "http://www.youtube.com", "W-1 Fri 15:00")
		reportNetworkActivity(bob, ["YouTube"], "http://www.youtube.com", "W-1 Sat 21:00")

		richard = appService.reloadUser(richard)
		Goal budgetGoalNewsRichard = richard.findActiveGoal(NEWS_ACT_CAT_URL)
		Goal timeZoneGoalSocialRichard = richard.findActiveGoal(SOCIAL_ACT_CAT_URL)
		Goal budgetGoalSocialBob = richard.buddies[0].findActiveGoal(SOCIAL_ACT_CAT_URL)
		Goal timeZoneGoalMultimediaBob = richard.buddies[0].findActiveGoal(MULTIMEDIA_ACT_CAT_URL)

		def expectedValuesRichardLastWeek = [
			"Mon" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: false, minutesBeyondGoal: 20, spread: [13 : 15, 14 : 5]]]],
			"Tue" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: false, minutesBeyondGoal: 25, spread: [35 : 15, 36 : 10]]]],
			"Wed" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalSocialRichard, data: [goalAccomplished: false, minutesBeyondGoal: 1, spread: [68 : 1]]]],
			"Thu" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalSocialRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [48 : 1]]]],
			"Fri" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalSocialRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [ : ]]]],
			"Sat" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalSocialRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [ : ]]]]]
		def expectedValuesBobLastWeek = [
			"Mon" : [],
			"Tue" : [],
			"Wed" : [],
			"Thu" : [[goal:budgetGoalSocialBob, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [80 : 15, 81 : 15, 82: 5]]]],
			"Fri" : [[goal:budgetGoalSocialBob, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalMultimediaBob, data: [goalAccomplished: false, minutesBeyondGoal: 1, spread: [60 : 1]]]],
			"Sat" : [[goal:budgetGoalSocialBob, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalMultimediaBob, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [84 : 1]]]]]
		def expectedValuesWithBuddiesLastWeek = [[ user : richard, expectedValues: expectedValuesRichardLastWeek], [ user : richard.buddies[0].user, expectedValues: expectedValuesBobLastWeek]]

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
		def responseBuddyWeekOverviews = appService.getWeekActivityOverviews(richard, richard.buddies[0])
		def responseBuddyDayOverviews = appService.getDayActivityOverviews(richard, richard.buddies[0], ["size": 14])

		then:
		assertWeekOverviewBasics(responseWeekOverviews, [3, 2], expectedTotalWeeks)
		assertWeekDateForCurrentWeek(responseWeekOverviews)

		def weekOverviewLastWeek = responseWeekOverviews.responseData._embedded."yona:weekActivityOverviews"[1]
		assertNumberOfReportedDaysForGoalInWeekOverview(weekOverviewLastWeek, budgetGoalNewsRichard, 6)
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, budgetGoalNewsRichard, expectedValuesRichardLastWeek, "Mon")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, budgetGoalNewsRichard, expectedValuesRichardLastWeek, "Tue")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, budgetGoalNewsRichard, expectedValuesRichardLastWeek, "Wed")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, budgetGoalNewsRichard, expectedValuesRichardLastWeek, "Thu")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, budgetGoalNewsRichard, expectedValuesRichardLastWeek, "Fri")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, budgetGoalNewsRichard, expectedValuesRichardLastWeek, "Sat")

		assertWeekDetailForGoal(richard, weekOverviewLastWeek, budgetGoalNewsRichard, expectedValuesRichardLastWeek)

		assertNumberOfReportedDaysForGoalInWeekOverview(weekOverviewLastWeek, timeZoneGoalSocialRichard, 4)
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, timeZoneGoalSocialRichard, expectedValuesRichardLastWeek, "Wed")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, timeZoneGoalSocialRichard, expectedValuesRichardLastWeek, "Thu")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, timeZoneGoalSocialRichard, expectedValuesRichardLastWeek, "Fri")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, timeZoneGoalSocialRichard, expectedValuesRichardLastWeek, "Sat")

		assertDayOverviewBasics(responseDayOverviewsAll, expectedTotalDays, expectedTotalDays, 14)
		assertDayOverviewBasics(responseDayOverviewsPage1, 3, expectedTotalDays)
		assertDayOverviewBasics(responseDayOverviewsPage2, 3, expectedTotalDays)
		assertDayOverviewForBudgetGoal(responseDayOverviewsAll, budgetGoalNewsRichard, expectedValuesRichardLastWeek, 1, "Mon")
		assertDayOverviewForBudgetGoal(responseDayOverviewsAll, budgetGoalNewsRichard, expectedValuesRichardLastWeek, 1, "Tue")
		assertDayOverviewForBudgetGoal(responseDayOverviewsAll, budgetGoalNewsRichard, expectedValuesRichardLastWeek, 1, "Wed")
		assertDayOverviewForBudgetGoal(responseDayOverviewsAll, budgetGoalNewsRichard, expectedValuesRichardLastWeek, 1, "Thu")
		assertDayOverviewForBudgetGoal(responseDayOverviewsAll, budgetGoalNewsRichard, expectedValuesRichardLastWeek, 1, "Fri")
		assertDayOverviewForBudgetGoal(responseDayOverviewsAll, budgetGoalNewsRichard, expectedValuesRichardLastWeek, 1, "Sat")

		assertDayOverviewForTimeZoneGoal(responseDayOverviewsAll, timeZoneGoalSocialRichard, expectedValuesRichardLastWeek, 1, "Wed")
		assertDayOverviewForTimeZoneGoal(responseDayOverviewsAll, timeZoneGoalSocialRichard, expectedValuesRichardLastWeek, 1, "Thu")
		assertDayOverviewForTimeZoneGoal(responseDayOverviewsAll, timeZoneGoalSocialRichard, expectedValuesRichardLastWeek, 1, "Fri")
		assertDayOverviewForTimeZoneGoal(responseDayOverviewsAll, timeZoneGoalSocialRichard, expectedValuesRichardLastWeek, 1, "Sat")

		assertDayDetail(richard, responseDayOverviewsAll, budgetGoalNewsRichard, expectedValuesRichardLastWeek, 1, "Mon")
		assertDayDetail(richard, responseDayOverviewsAll, budgetGoalNewsRichard, expectedValuesRichardLastWeek, 1, "Tue")
		assertDayDetail(richard, responseDayOverviewsAll, budgetGoalNewsRichard, expectedValuesRichardLastWeek, 1, "Wed")
		assertDayDetail(richard, responseDayOverviewsAll, budgetGoalNewsRichard, expectedValuesRichardLastWeek, 1, "Thu")
		assertDayDetail(richard, responseDayOverviewsAll, budgetGoalNewsRichard, expectedValuesRichardLastWeek, 1, "Fri")
		assertDayDetail(richard, responseDayOverviewsAll, budgetGoalNewsRichard, expectedValuesRichardLastWeek, 1, "Sat")

		assertDayDetail(richard, responseDayOverviewsAll, timeZoneGoalSocialRichard, expectedValuesRichardLastWeek, 1, "Wed")
		assertDayDetail(richard, responseDayOverviewsAll, timeZoneGoalSocialRichard, expectedValuesRichardLastWeek, 1, "Thu")
		assertDayDetail(richard, responseDayOverviewsAll, timeZoneGoalSocialRichard, expectedValuesRichardLastWeek, 1, "Fri")
		assertDayDetail(richard, responseDayOverviewsAll, timeZoneGoalSocialRichard, expectedValuesRichardLastWeek, 1, "Sat")

		assertDayOverviewWithBuddiesBasics(responseDayOverviewsWithBuddies, expectedTotalDays, expectedTotalDays, 14)
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, NEWS_ACT_CAT_URL, expectedValuesWithBuddiesLastWeek, 1, "Mon")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, NEWS_ACT_CAT_URL, expectedValuesWithBuddiesLastWeek, 1, "Tue")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, NEWS_ACT_CAT_URL, expectedValuesWithBuddiesLastWeek, 1, "Wed")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, NEWS_ACT_CAT_URL, expectedValuesWithBuddiesLastWeek, 1, "Thu")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, NEWS_ACT_CAT_URL, expectedValuesWithBuddiesLastWeek, 1, "Fri")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, NEWS_ACT_CAT_URL, expectedValuesWithBuddiesLastWeek, 1, "Sat")

		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, SOCIAL_ACT_CAT_URL, expectedValuesWithBuddiesLastWeek, 1, "Wed")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, SOCIAL_ACT_CAT_URL, expectedValuesWithBuddiesLastWeek, 1, "Thu")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, SOCIAL_ACT_CAT_URL, expectedValuesWithBuddiesLastWeek, 1, "Fri")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, SOCIAL_ACT_CAT_URL, expectedValuesWithBuddiesLastWeek, 1, "Sat")

		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, MULTIMEDIA_ACT_CAT_URL, expectedValuesWithBuddiesLastWeek, 1, "Thu")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, MULTIMEDIA_ACT_CAT_URL, expectedValuesWithBuddiesLastWeek, 1, "Fri")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, MULTIMEDIA_ACT_CAT_URL, expectedValuesWithBuddiesLastWeek, 1, "Sat")

		def weekOverviewBuddyLastWeek = responseBuddyWeekOverviews.responseData._embedded."yona:weekActivityOverviews"[1]
		assertNumberOfReportedDaysForGoalInWeekOverview(weekOverviewBuddyLastWeek, budgetGoalSocialBob, 3)
		assertDayInWeekOverviewForGoal(weekOverviewBuddyLastWeek, budgetGoalSocialBob, expectedValuesBobLastWeek, "Thu")
		assertDayInWeekOverviewForGoal(weekOverviewBuddyLastWeek, budgetGoalSocialBob, expectedValuesBobLastWeek, "Fri")
		assertDayInWeekOverviewForGoal(weekOverviewBuddyLastWeek, budgetGoalSocialBob, expectedValuesBobLastWeek, "Sat")

		assertWeekDetailForGoal(richard, weekOverviewBuddyLastWeek, budgetGoalSocialBob, expectedValuesBobLastWeek)

		assertNumberOfReportedDaysForGoalInWeekOverview(weekOverviewBuddyLastWeek, timeZoneGoalMultimediaBob, 2)
		assertDayInWeekOverviewForGoal(weekOverviewBuddyLastWeek, timeZoneGoalMultimediaBob, expectedValuesBobLastWeek, "Fri")
		assertDayInWeekOverviewForGoal(weekOverviewBuddyLastWeek, timeZoneGoalMultimediaBob, expectedValuesBobLastWeek, "Sat")

		assertDayDetail(richard, responseBuddyDayOverviews, budgetGoalSocialBob, expectedValuesBobLastWeek, 1, "Thu")
		assertDayDetail(richard, responseBuddyDayOverviews, budgetGoalSocialBob, expectedValuesBobLastWeek, 1, "Fri")
		assertDayDetail(richard, responseBuddyDayOverviews, budgetGoalSocialBob, expectedValuesBobLastWeek, 1, "Sat")

		assertDayDetail(richard, responseBuddyDayOverviews, timeZoneGoalMultimediaBob, expectedValuesBobLastWeek, 1, "Fri")
		assertDayDetail(richard, responseBuddyDayOverviews, timeZoneGoalMultimediaBob, expectedValuesBobLastWeek, 1, "Sat")

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
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

	def 'Add activity after retrieving the report with multiple days'()
	{
		given:
		User richard = addRichard()
		def ZonedDateTime now = YonaServer.now
		setGoalCreationTime(richard, NEWS_ACT_CAT_URL, "W-1 Mon 02:18")
		Goal budgetGoalGambling = richard.findActiveGoal(GAMBLING_ACT_CAT_URL)
		Goal budgetGoalNews = richard.findActiveGoal(NEWS_ACT_CAT_URL)
		def initialExpectedValuesRichardLastWeek = [
			"Mon" : [[goal:budgetGoalNews, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Tue" : [[goal:budgetGoalNews, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Wed" : [[goal:budgetGoalNews, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Thu" : [[goal:budgetGoalNews, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Fri" : [[goal:budgetGoalNews, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Sat" : [[goal:budgetGoalNews, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
		]
		def initialResponseWeekOverviews = appService.getWeekActivityOverviews(richard)
		def initialResponseDayOverviews = appService.getDayActivityOverviews(richard, ["size": 14])
		assert initialResponseWeekOverviews.status == 200
		def initialCurrentWeekOverviewLastWeek = initialResponseWeekOverviews.responseData._embedded."yona:weekActivityOverviews"[1]
		assertDayInWeekOverviewForGoal(initialCurrentWeekOverviewLastWeek, budgetGoalNews, initialExpectedValuesRichardLastWeek, "Wed")
		assertWeekDetailForGoal(richard, initialCurrentWeekOverviewLastWeek, budgetGoalNews, initialExpectedValuesRichardLastWeek)
		assertDayOverviewForBudgetGoal(initialResponseDayOverviews, budgetGoalNews, initialExpectedValuesRichardLastWeek, 1, "Wed")
		assertDayDetail(richard, initialResponseDayOverviews, budgetGoalNews, initialExpectedValuesRichardLastWeek, 1, "Wed")

		when:
		reportAppActivity(richard, "NU.nl", "W-1 Wed 03:15", "W-1 Wed 03:35")

		then:
		def expectedValuesRichardLastWeekAfterActivity = [
			"Mon" : [[goal:budgetGoalNews, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Tue" : [[goal:budgetGoalNews, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Wed" : [[goal:budgetGoalNews, data: [goalAccomplished: false, minutesBeyondGoal: 20, spread: [13 : 15, 14 : 5]]]],
			"Thu" : [[goal:budgetGoalNews, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Fri" : [[goal:budgetGoalNews, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Sat" : [[goal:budgetGoalNews, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
		]
		def responseWeekOverviewsAfterActivity = appService.getWeekActivityOverviews(richard)
		def currentWeekOverviewAfterActivity = responseWeekOverviewsAfterActivity.responseData._embedded."yona:weekActivityOverviews"[1]
		assertDayInWeekOverviewForGoal(currentWeekOverviewAfterActivity, budgetGoalNews, expectedValuesRichardLastWeekAfterActivity, "Tue")
		assertDayInWeekOverviewForGoal(currentWeekOverviewAfterActivity, budgetGoalNews, expectedValuesRichardLastWeekAfterActivity, "Wed")
		assertDayInWeekOverviewForGoal(currentWeekOverviewAfterActivity, budgetGoalNews, expectedValuesRichardLastWeekAfterActivity, "Thu")
		assertWeekDetailForGoal(richard, currentWeekOverviewAfterActivity, budgetGoalNews, expectedValuesRichardLastWeekAfterActivity)

		def responseDayOverviewsAfterActivity = appService.getDayActivityOverviews(richard, ["size": 14])
		assertDayOverviewForBudgetGoal(responseDayOverviewsAfterActivity, budgetGoalNews, expectedValuesRichardLastWeekAfterActivity, 1, "Tue")
		assertDayOverviewForBudgetGoal(responseDayOverviewsAfterActivity, budgetGoalNews, expectedValuesRichardLastWeekAfterActivity, 1, "Wed")
		assertDayOverviewForBudgetGoal(responseDayOverviewsAfterActivity, budgetGoalNews, expectedValuesRichardLastWeekAfterActivity, 1, "Thu")
		assertDayDetail(richard, responseDayOverviewsAfterActivity, budgetGoalNews, expectedValuesRichardLastWeekAfterActivity, 1, "Tue")
		assertDayDetail(richard, responseDayOverviewsAfterActivity, budgetGoalNews, expectedValuesRichardLastWeekAfterActivity, 1, "Wed")
		assertDayDetail(richard, responseDayOverviewsAfterActivity, budgetGoalNews, expectedValuesRichardLastWeekAfterActivity, 1, "Thu")

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Remove goal after adding activities'()
	{
		given:
		User richard = addRichard()
		setGoalCreationTime(richard, NEWS_ACT_CAT_URL, "W-1 Mon 02:18")
		reportAppActivity(richard, "NU.nl", "W-1 Mon 03:15", "W-1 Mon 03:35")
		reportAppActivities(richard, [createAppActivity("NU.nl", "W-1 Tue 08:45", "W-1 Tue 09:10"), createAppActivity("Facebook", "W-1 Tue 09:35", "W-1 Tue 10:10")])
		addTimeZoneGoal(richard, SOCIAL_ACT_CAT_URL, ["11:00-12:00"], "W-1 Wed 13:55")
		reportNetworkActivity(richard, ["social"], "http://www.facebook.com", "W-1 Wed 15:00")
		reportNetworkActivity(richard, ["social"], "http://www.facebook.com", "W-1 Thu 11:30")

		richard = appService.reloadUser(richard)
		Goal budgetGoalNews = richard.findActiveGoal(NEWS_ACT_CAT_URL)
		Goal timeZoneGoalSocial = richard.findActiveGoal(SOCIAL_ACT_CAT_URL)

		def expectedValuesRichardLastWeekBeforeDelete = [
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
		assertDayInWeekOverviewForGoal(weekOverviewLastWeekBeforeDelete, budgetGoalNews, expectedValuesRichardLastWeekBeforeDelete, "Mon")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeekBeforeDelete, budgetGoalNews, expectedValuesRichardLastWeekBeforeDelete, "Tue")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeekBeforeDelete, budgetGoalNews, expectedValuesRichardLastWeekBeforeDelete, "Wed")

		assertWeekDetailForGoal(richard, weekOverviewLastWeekBeforeDelete, budgetGoalNews, expectedValuesRichardLastWeekBeforeDelete)

		assertDayOverviewBasics(responseDayOverviewsAllBeforeDelete, expectedTotalDaysBeforeDelete, expectedTotalDaysBeforeDelete, 14)
		assertDayOverviewForBudgetGoal(responseDayOverviewsAllBeforeDelete, budgetGoalNews, expectedValuesRichardLastWeekBeforeDelete, 1, "Mon")
		assertDayOverviewForBudgetGoal(responseDayOverviewsAllBeforeDelete, budgetGoalNews, expectedValuesRichardLastWeekBeforeDelete, 1, "Tue")
		assertDayOverviewForBudgetGoal(responseDayOverviewsAllBeforeDelete, budgetGoalNews, expectedValuesRichardLastWeekBeforeDelete, 1, "Wed")

		assertDayDetail(richard, responseDayOverviewsAllBeforeDelete, budgetGoalNews, expectedValuesRichardLastWeekBeforeDelete, 1, "Mon")
		assertDayDetail(richard, responseDayOverviewsAllBeforeDelete, budgetGoalNews, expectedValuesRichardLastWeekBeforeDelete, 1, "Tue")
		assertDayDetail(richard, responseDayOverviewsAllBeforeDelete, budgetGoalNews, expectedValuesRichardLastWeekBeforeDelete, 1, "Wed")

		assertNumberOfReportedDaysForGoalInWeekOverview(weekOverviewLastWeekBeforeDelete, timeZoneGoalSocial, 4)
		assertDayInWeekOverviewForGoal(weekOverviewLastWeekBeforeDelete, timeZoneGoalSocial, expectedValuesRichardLastWeekBeforeDelete, "Wed")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeekBeforeDelete, timeZoneGoalSocial, expectedValuesRichardLastWeekBeforeDelete, "Thu")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeekBeforeDelete, timeZoneGoalSocial, expectedValuesRichardLastWeekBeforeDelete, "Fri")

		assertDayDetail(richard, responseDayOverviewsAllBeforeDelete, timeZoneGoalSocial, expectedValuesRichardLastWeekBeforeDelete, 1, "Wed")
		assertDayDetail(richard, responseDayOverviewsAllBeforeDelete, timeZoneGoalSocial, expectedValuesRichardLastWeekBeforeDelete, 1, "Thu")
		assertDayDetail(richard, responseDayOverviewsAllBeforeDelete, timeZoneGoalSocial, expectedValuesRichardLastWeekBeforeDelete, 1, "Fri")

		when:
		def response = appService.removeGoal(richard, budgetGoalNews)

		then:
		def expectedTotalDaysAfterDelete = expectedTotalDaysBeforeDelete - 2
		def expectedTotalWeeksAfterDelete = expectedTotalWeeksBeforeDelete
		def expectedValuesRichardLastWeekAfterDelete = [
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
		assertDayInWeekOverviewForGoal(weekOverviewLastWeekAfterDelete, timeZoneGoalSocial, expectedValuesRichardLastWeekAfterDelete, "Wed")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeekAfterDelete, timeZoneGoalSocial, expectedValuesRichardLastWeekAfterDelete, "Thu")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeekAfterDelete, timeZoneGoalSocial, expectedValuesRichardLastWeekAfterDelete, "Fri")

		def responseDayOverviewsAllAfterDelete = appService.getDayActivityOverviews(richard, ["size": 14])
		assertDayOverviewBasics(responseDayOverviewsAllAfterDelete, expectedTotalDaysAfterDelete, expectedTotalDaysAfterDelete, 14)
		assertDayDetail(richard, responseDayOverviewsAllAfterDelete, timeZoneGoalSocial, expectedValuesRichardLastWeekAfterDelete, 1, "Wed")
		assertDayDetail(richard, responseDayOverviewsAllAfterDelete, timeZoneGoalSocial, expectedValuesRichardLastWeekAfterDelete, 1, "Thu")
		assertDayDetail(richard, responseDayOverviewsAllAfterDelete, timeZoneGoalSocial, expectedValuesRichardLastWeekAfterDelete, 1, "Fri")

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

	def 'Retrieve inactive activity report current day/week before and after update of time zone goal'()
	{
		given:
		User richard = addRichard()
		Goal timeZoneGoalMultimedia = addTimeZoneGoal(richard, MULTIMEDIA_ACT_CAT_URL, ["11:00-12:00"])
		assert appService.getDayActivityOverviews(richard).status == 200
		assert appService.getWeekActivityOverviews(richard).status == 200

		when:
		updateTimeZoneGoal(richard, timeZoneGoalMultimedia, ["13:00-14:00"])

		then:
		appService.getDayActivityOverviews(richard).status == 200
		appService.getWeekActivityOverviews(richard).status == 200

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Retrieve inactive activity report current day/week before and after update of budget goal'()
	{
		given:
		User richard = addRichard()
		Goal budgetGoalMultimedia = addBudgetGoal(richard, MULTIMEDIA_ACT_CAT_URL, 60)
		assert appService.getDayActivityOverviews(richard).status == 200
		assert appService.getWeekActivityOverviews(richard).status == 200

		when:
		updateBudgetGoal(richard, budgetGoalMultimedia, 81)

		then:
		appService.getDayActivityOverviews(richard).status == 200
		appService.getWeekActivityOverviews(richard).status == 200

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Retrieve inactive activity report past week, after goal update in earlier week'()
	{
		given:
		User richard = addRichard()
		Goal budgetGoalMultimediaBeforeUpdate = addBudgetGoal(richard, MULTIMEDIA_ACT_CAT_URL, 60, "W-2 Tue 13:30")
		updateBudgetGoal(richard, budgetGoalMultimediaBeforeUpdate, 81, "W-1 Mon 18:30")
		richard = appService.reloadUser(richard)
		budgetGoalMultimediaBeforeUpdate = richard.goals.find{ it.activityCategoryUrl == MULTIMEDIA_ACT_CAT_URL && it.historyItem }
		BudgetGoal budgetGoalMultimediaAfterUpdate = richard.findActiveGoal(MULTIMEDIA_ACT_CAT_URL)

		def expectedValuesRichardWeekBeforeLastWeek = [
			"Tue" : [[goal:budgetGoalMultimediaBeforeUpdate, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Wed" : [[goal:budgetGoalMultimediaBeforeUpdate, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Thu" : [[goal:budgetGoalMultimediaBeforeUpdate, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Fri" : [[goal:budgetGoalMultimediaBeforeUpdate, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Sat" : [[goal:budgetGoalMultimediaBeforeUpdate, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]]]
		def expectedValuesRichardLastWeek = [
			"Sun" : [[goal:budgetGoalMultimediaBeforeUpdate, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Mon" : [[goal:budgetGoalMultimediaAfterUpdate, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Tue" : [[goal:budgetGoalMultimediaAfterUpdate, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Wed" : [[goal:budgetGoalMultimediaAfterUpdate, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Thu" : [[goal:budgetGoalMultimediaAfterUpdate, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Fri" : [[goal:budgetGoalMultimediaAfterUpdate, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Sat" : [[goal:budgetGoalMultimediaAfterUpdate, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]]]

		def currentDayOfWeek = YonaServer.getCurrentDayOfWeek()
		def expectedTotalDays = 5 + 7 + currentDayOfWeek + 1
		def expectedTotalWeeks = 3

		when:
		def responseWeekOverviews = appService.getWeekActivityOverviews(richard, ["size": expectedTotalWeeks])
		def responseDayOverviewsAll = appService.getDayActivityOverviews(richard, ["size": 21])

		then:
		assertWeekOverviewBasics(responseWeekOverviews, [3, 1, 1], expectedTotalWeeks, expectedTotalWeeks)
		assertWeekDateForCurrentWeek(responseWeekOverviews)

		def weekOverviewWeekBeforeLastWeek = responseWeekOverviews.responseData._embedded."yona:weekActivityOverviews"[2]
		assertNumberOfReportedDaysForGoalInWeekOverview(weekOverviewWeekBeforeLastWeek, budgetGoalMultimediaBeforeUpdate, 5)
		assertDayInWeekOverviewForGoal(weekOverviewWeekBeforeLastWeek, budgetGoalMultimediaBeforeUpdate, expectedValuesRichardWeekBeforeLastWeek, "Tue")
		assertDayInWeekOverviewForGoal(weekOverviewWeekBeforeLastWeek, budgetGoalMultimediaBeforeUpdate, expectedValuesRichardWeekBeforeLastWeek, "Wed")
		assertDayInWeekOverviewForGoal(weekOverviewWeekBeforeLastWeek, budgetGoalMultimediaBeforeUpdate, expectedValuesRichardWeekBeforeLastWeek, "Thu")
		assertDayInWeekOverviewForGoal(weekOverviewWeekBeforeLastWeek, budgetGoalMultimediaBeforeUpdate, expectedValuesRichardWeekBeforeLastWeek, "Fri")
		assertDayInWeekOverviewForGoal(weekOverviewWeekBeforeLastWeek, budgetGoalMultimediaBeforeUpdate, expectedValuesRichardWeekBeforeLastWeek, "Sat")

		def weekOverviewLastWeek = responseWeekOverviews.responseData._embedded."yona:weekActivityOverviews"[1]
		assertNumberOfReportedDaysForGoalInWeekOverview(weekOverviewLastWeek, budgetGoalMultimediaAfterUpdate, 7)
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, budgetGoalMultimediaBeforeUpdate, expectedValuesRichardLastWeek, "Sun")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, budgetGoalMultimediaAfterUpdate, expectedValuesRichardLastWeek, "Mon")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, budgetGoalMultimediaAfterUpdate, expectedValuesRichardLastWeek, "Tue")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, budgetGoalMultimediaAfterUpdate, expectedValuesRichardLastWeek, "Wed")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, budgetGoalMultimediaAfterUpdate, expectedValuesRichardLastWeek, "Thu")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, budgetGoalMultimediaAfterUpdate, expectedValuesRichardLastWeek, "Fri")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, budgetGoalMultimediaAfterUpdate, expectedValuesRichardLastWeek, "Sat")

		assertWeekDetailForGoal(richard, weekOverviewLastWeek, budgetGoalMultimediaAfterUpdate, expectedValuesRichardLastWeek)

		assertDayOverviewBasics(responseDayOverviewsAll, expectedTotalDays, expectedTotalDays, 21)
		assertDayOverviewForBudgetGoal(responseDayOverviewsAll, budgetGoalMultimediaBeforeUpdate, expectedValuesRichardWeekBeforeLastWeek, 2, "Tue")
		assertDayOverviewForBudgetGoal(responseDayOverviewsAll, budgetGoalMultimediaBeforeUpdate, expectedValuesRichardWeekBeforeLastWeek, 2, "Wed")
		assertDayOverviewForBudgetGoal(responseDayOverviewsAll, budgetGoalMultimediaBeforeUpdate, expectedValuesRichardWeekBeforeLastWeek, 2, "Thu")
		assertDayOverviewForBudgetGoal(responseDayOverviewsAll, budgetGoalMultimediaBeforeUpdate, expectedValuesRichardWeekBeforeLastWeek, 2, "Fri")
		assertDayOverviewForBudgetGoal(responseDayOverviewsAll, budgetGoalMultimediaBeforeUpdate, expectedValuesRichardWeekBeforeLastWeek, 2, "Sat")

		assertDayOverviewForBudgetGoal(responseDayOverviewsAll, budgetGoalMultimediaBeforeUpdate, expectedValuesRichardLastWeek, 1, "Sun")
		assertDayOverviewForBudgetGoal(responseDayOverviewsAll, budgetGoalMultimediaAfterUpdate, expectedValuesRichardLastWeek, 1, "Mon")
		assertDayOverviewForBudgetGoal(responseDayOverviewsAll, budgetGoalMultimediaAfterUpdate, expectedValuesRichardLastWeek, 1, "Tue")
		assertDayOverviewForBudgetGoal(responseDayOverviewsAll, budgetGoalMultimediaAfterUpdate, expectedValuesRichardLastWeek, 1, "Wed")
		assertDayOverviewForBudgetGoal(responseDayOverviewsAll, budgetGoalMultimediaAfterUpdate, expectedValuesRichardLastWeek, 1, "Thu")
		assertDayOverviewForBudgetGoal(responseDayOverviewsAll, budgetGoalMultimediaAfterUpdate, expectedValuesRichardLastWeek, 1, "Fri")
		assertDayOverviewForBudgetGoal(responseDayOverviewsAll, budgetGoalMultimediaAfterUpdate, expectedValuesRichardLastWeek, 1, "Sat")

		assertDayDetail(richard, responseDayOverviewsAll, budgetGoalMultimediaBeforeUpdate, expectedValuesRichardWeekBeforeLastWeek, 2, "Tue")
		assertDayDetail(richard, responseDayOverviewsAll, budgetGoalMultimediaBeforeUpdate, expectedValuesRichardWeekBeforeLastWeek, 2, "Wed")
		assertDayDetail(richard, responseDayOverviewsAll, budgetGoalMultimediaBeforeUpdate, expectedValuesRichardWeekBeforeLastWeek, 2, "Thu")
		assertDayDetail(richard, responseDayOverviewsAll, budgetGoalMultimediaBeforeUpdate, expectedValuesRichardWeekBeforeLastWeek, 2, "Fri")
		assertDayDetail(richard, responseDayOverviewsAll, budgetGoalMultimediaBeforeUpdate, expectedValuesRichardWeekBeforeLastWeek, 2, "Sat")

		assertDayDetail(richard, responseDayOverviewsAll, budgetGoalMultimediaAfterUpdate, expectedValuesRichardLastWeek, 1, "Mon")
		assertDayDetail(richard, responseDayOverviewsAll, budgetGoalMultimediaAfterUpdate, expectedValuesRichardLastWeek, 1, "Tue")
		assertDayDetail(richard, responseDayOverviewsAll, budgetGoalMultimediaAfterUpdate, expectedValuesRichardLastWeek, 1, "Wed")
		assertDayDetail(richard, responseDayOverviewsAll, budgetGoalMultimediaAfterUpdate, expectedValuesRichardLastWeek, 1, "Thu")
		assertDayDetail(richard, responseDayOverviewsAll, budgetGoalMultimediaAfterUpdate, expectedValuesRichardLastWeek, 1, "Fri")
		assertDayDetail(richard, responseDayOverviewsAll, budgetGoalMultimediaAfterUpdate, expectedValuesRichardLastWeek, 1, "Sat")

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Retrieve activity report after goal update'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob

		// Week -2
		// Monday
		setGoalCreationTime(richard, NEWS_ACT_CAT_URL, "W-2 Mon 02:18")
		BudgetGoal budgetGoalNewsRichardBeforeUpdate = richard.findActiveGoal(NEWS_ACT_CAT_URL)
		reportAppActivity(richard, "NU.nl", "W-2 Mon 03:15", "W-2 Mon 03:35")

		// Tuesday
		reportAppActivities(richard, [createAppActivity("NU.nl", "W-2 Tue 08:45", "W-2 Tue 09:10"), createAppActivity("Facebook", "W-2 Tue 09:35", "W-2 Tue 10:10")])

		// Thursday
		BudgetGoal budgetGoalSocialBobBeforeUpdate = addBudgetGoal(bob, SOCIAL_ACT_CAT_URL, 180, "W-2 Thu 18:00")
		reportAppActivity(bob, "Facebook", "W-2 Thu 20:00", "W-2 Thu 20:35")

		// Friday
		reportNetworkActivity(bob, ["YouTube"], "http://www.youtube.com", "W-2 Fri 09:00") // Should be ignored, as there was no goal yet
		TimeZoneGoal timeZoneGoalMultimediaBobBeforeUpdate = addTimeZoneGoal(bob, MULTIMEDIA_ACT_CAT_URL, ["20:00-22:00"], "W-2 Fri 14:00")
		reportNetworkActivity(bob, ["YouTube"], "http://www.youtube.com", "W-2 Fri 15:00")
		reportNetworkActivity(bob, ["YouTube"], "http://www.youtube.com", "W-2 Fri 21:00")

		// Week -1
		// Wednesday
		reportAppActivity(richard, "NU.nl", "W-1 Wed 09:13", "W-1 Wed 10:07")
		updateBudgetGoal(richard, budgetGoalNewsRichardBeforeUpdate, 60, "W-1 Wed 20:51")
		reportAppActivity(richard, "NU.nl", "W-1 Wed 21:43", "W-1 Wed 22:01")

		// Thursday
		TimeZoneGoal timeZoneGoalSocialRichardBeforeUpdate = addTimeZoneGoal(richard, SOCIAL_ACT_CAT_URL, ["11:00-12:00"], "W-1 Thu 13:55")
		reportNetworkActivity(richard, ["social"], "http://www.facebook.com", "W-1 Thu 15:00")

		// Friday
		updateTimeZoneGoal(richard, timeZoneGoalSocialRichardBeforeUpdate, ["11:00-12:00", "17:30-21:30"], , "W-1 Fri 12:03")

		reportAppActivity(bob, "com.google.android.youtube", "W-1 Fri 10:13", "W-1 Fri 10:35")
		updateTimeZoneGoal(bob, timeZoneGoalMultimediaBobBeforeUpdate, ["10:00-11:00", "20:00-22:00"], , "W-1 Fri 12:03")
		reportAppActivity(bob, "com.google.android.youtube", "W-1 Fri 21:43", "W-1 Fri 21:59")

		// Saturday
		reportAppActivity(richard, "Facebook", "W-1 Sat 20:43", "W-1 Sat 21:01")

		updateBudgetGoal(bob, budgetGoalSocialBobBeforeUpdate, 120, "W-1 Sat 20:51")
		reportAppActivity(bob, "Facebook", "W-1 Sat 20:55", "W-1 Sat 22:00")

		richard = appService.reloadUser(richard)
		budgetGoalNewsRichardBeforeUpdate = richard.goals.find{ it.activityCategoryUrl == NEWS_ACT_CAT_URL && it.historyItem }
		BudgetGoal budgetGoalNewsRichardAfterUpdate = richard.findActiveGoal(NEWS_ACT_CAT_URL)
		timeZoneGoalSocialRichardBeforeUpdate = richard.goals.find{ it.activityCategoryUrl == SOCIAL_ACT_CAT_URL && it.historyItem }
		TimeZoneGoal timeZoneGoalSocialRichardAfterUpdate = richard.findActiveGoal(SOCIAL_ACT_CAT_URL)

		budgetGoalSocialBobBeforeUpdate = richard.buddies[0].goals.find{ it.activityCategoryUrl == SOCIAL_ACT_CAT_URL && it.historyItem }
		BudgetGoal budgetGoalSocialBobAfterUpdate = richard.buddies[0].goals.find{ it.activityCategoryUrl == SOCIAL_ACT_CAT_URL && !it.historyItem}
		timeZoneGoalMultimediaBobBeforeUpdate = richard.buddies[0].goals.find{ it.activityCategoryUrl == MULTIMEDIA_ACT_CAT_URL && it.historyItem }
		TimeZoneGoal timeZoneGoalMultimediaBobAfterUpdate = richard.buddies[0].goals.find{ it.activityCategoryUrl == MULTIMEDIA_ACT_CAT_URL && !it.historyItem}

		def expectedValuesRichardWeekBeforeLastWeek = [
			"Mon" : [[goal:budgetGoalNewsRichardBeforeUpdate, data: [goalAccomplished: false, minutesBeyondGoal: 20, spread: [13 : 15, 14 : 5]]]],
			"Tue" : [[goal:budgetGoalNewsRichardBeforeUpdate, data: [goalAccomplished: false, minutesBeyondGoal: 25, spread: [35 : 15, 36 : 10]]]],
			"Wed" : [[goal:budgetGoalNewsRichardBeforeUpdate, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Thu" : [[goal:budgetGoalNewsRichardBeforeUpdate, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Fri" : [[goal:budgetGoalNewsRichardBeforeUpdate, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Sat" : [[goal:budgetGoalNewsRichardBeforeUpdate, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]]]
		def expectedValuesBobWeekBeforeLastWeek = [
			"Thu" : [[goal:budgetGoalSocialBobBeforeUpdate, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [80 : 15, 81 : 15, 82 : 5]]]],
			"Fri" : [[goal:budgetGoalSocialBobBeforeUpdate, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalMultimediaBobBeforeUpdate, data: [goalAccomplished: false, minutesBeyondGoal: 1, spread: [60 : 1, 84 : 1]]]],
			"Sat" : [[goal:budgetGoalSocialBobBeforeUpdate, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalMultimediaBobBeforeUpdate, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]]]
		def expectedValuesWithBuddiesWeekBeforeLastWeek = [[ user : richard, expectedValues: expectedValuesRichardWeekBeforeLastWeek], [ user : richard.buddies[0].user, expectedValues: expectedValuesBobWeekBeforeLastWeek]]

		def expectedValuesRichardLastWeek = [
			"Sun" : [[goal:budgetGoalNewsRichardBeforeUpdate, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Mon" : [[goal:budgetGoalNewsRichardBeforeUpdate, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Tue" : [[goal:budgetGoalNewsRichardBeforeUpdate, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Wed" : [[goal:budgetGoalNewsRichardAfterUpdate, data: [goalAccomplished: false, minutesBeyondGoal: 12, spread: [36 : 2, 37 : 15, 38 : 15, 39 : 15, 40: 7, 86 : 2, 87 : 15, 88 : 1]]]],
			"Thu" : [[goal:budgetGoalNewsRichardAfterUpdate, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalSocialRichardBeforeUpdate, data: [goalAccomplished: false, minutesBeyondGoal: 1, spread: [68 : 1]]]],
			"Fri" : [[goal:budgetGoalNewsRichardAfterUpdate, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalSocialRichardAfterUpdate, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Sat" : [[goal:budgetGoalNewsRichardAfterUpdate, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalSocialRichardAfterUpdate, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [ 82 : 2, 83 : 15, 84 : 1]]]]]
		def expectedValuesBobLastWeek = [
			"Sun" : [[goal:budgetGoalSocialBobBeforeUpdate, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalMultimediaBobBeforeUpdate, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Mon" : [[goal:budgetGoalSocialBobBeforeUpdate, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalMultimediaBobBeforeUpdate, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Tue" : [[goal:budgetGoalSocialBobBeforeUpdate, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalMultimediaBobBeforeUpdate, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Wed" : [[goal:budgetGoalSocialBobBeforeUpdate, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalMultimediaBobBeforeUpdate, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Thu" : [[goal:budgetGoalSocialBobBeforeUpdate, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalMultimediaBobBeforeUpdate, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Fri" : [[goal:budgetGoalSocialBobBeforeUpdate, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalMultimediaBobAfterUpdate, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [ 40 : 2, 41 :15, 42 : 5, 86 : 2, 87 : 14]]]],
			"Sat" : [[goal:budgetGoalSocialBobAfterUpdate, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [83 : 5, 84: 15, 85: 15, 86: 15, 87: 15]]], [goal:timeZoneGoalMultimediaBobAfterUpdate, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]]]
		def expectedValuesWithBuddiesLastWeek = [[ user : richard, expectedValues: expectedValuesRichardLastWeek], [ user : richard.buddies[0].user, expectedValues: expectedValuesBobLastWeek]]

		def currentDayOfWeek = YonaServer.getCurrentDayOfWeek()
		def expectedTotalDays = 6 + 7 + currentDayOfWeek + 1
		def expectedTotalWeeks = 3

		when:
		def responseWeekOverviews = appService.getWeekActivityOverviews(richard, ["size": expectedTotalWeeks])
		def responseDayOverviewsAll = appService.getDayActivityOverviews(richard, ["size": 21])
		def responseDayOverviewsWithBuddies = appService.getDayActivityOverviewsWithBuddies(richard, ["size": 21])
		def responseBuddyWeekOverviews = appService.getWeekActivityOverviews(richard, richard.buddies[0], ["size": expectedTotalWeeks])
		def responseBuddyDayOverviews = appService.getDayActivityOverviews(richard, richard.buddies[0], ["size": 21])

		then:
		assertWeekOverviewBasics(responseWeekOverviews, [3, 2, 1], expectedTotalWeeks, expectedTotalWeeks)
		assertWeekDateForCurrentWeek(responseWeekOverviews)

		def weekOverviewWeekBeforeLastWeek = responseWeekOverviews.responseData._embedded."yona:weekActivityOverviews"[2]
		assertNumberOfReportedDaysForGoalInWeekOverview(weekOverviewWeekBeforeLastWeek, budgetGoalNewsRichardBeforeUpdate, 6)
		assertDayInWeekOverviewForGoal(weekOverviewWeekBeforeLastWeek, budgetGoalNewsRichardBeforeUpdate, expectedValuesRichardWeekBeforeLastWeek, "Mon")
		assertDayInWeekOverviewForGoal(weekOverviewWeekBeforeLastWeek, budgetGoalNewsRichardBeforeUpdate, expectedValuesRichardWeekBeforeLastWeek, "Tue")
		assertDayInWeekOverviewForGoal(weekOverviewWeekBeforeLastWeek, budgetGoalNewsRichardBeforeUpdate, expectedValuesRichardWeekBeforeLastWeek, "Wed")
		assertDayInWeekOverviewForGoal(weekOverviewWeekBeforeLastWeek, budgetGoalNewsRichardBeforeUpdate, expectedValuesRichardWeekBeforeLastWeek, "Thu")
		assertDayInWeekOverviewForGoal(weekOverviewWeekBeforeLastWeek, budgetGoalNewsRichardBeforeUpdate, expectedValuesRichardWeekBeforeLastWeek, "Fri")
		assertDayInWeekOverviewForGoal(weekOverviewWeekBeforeLastWeek, budgetGoalNewsRichardBeforeUpdate, expectedValuesRichardWeekBeforeLastWeek, "Sat")

		def weekOverviewLastWeek = responseWeekOverviews.responseData._embedded."yona:weekActivityOverviews"[1]
		assertNumberOfReportedDaysForGoalInWeekOverview(weekOverviewLastWeek, budgetGoalNewsRichardAfterUpdate, 7)
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, budgetGoalNewsRichardBeforeUpdate, expectedValuesRichardLastWeek, "Sun")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, budgetGoalNewsRichardBeforeUpdate, expectedValuesRichardLastWeek, "Mon")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, budgetGoalNewsRichardBeforeUpdate, expectedValuesRichardLastWeek, "Tue")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, budgetGoalNewsRichardAfterUpdate, expectedValuesRichardLastWeek, "Wed")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, budgetGoalNewsRichardAfterUpdate, expectedValuesRichardLastWeek, "Thu")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, budgetGoalNewsRichardAfterUpdate, expectedValuesRichardLastWeek, "Fri")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, budgetGoalNewsRichardAfterUpdate, expectedValuesRichardLastWeek, "Sat")

		assertWeekDetailForGoal(richard, weekOverviewLastWeek, budgetGoalNewsRichardAfterUpdate, expectedValuesRichardLastWeek)

		assertNumberOfReportedDaysForGoalInWeekOverview(weekOverviewLastWeek, timeZoneGoalSocialRichardAfterUpdate, 3)
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, timeZoneGoalSocialRichardBeforeUpdate, expectedValuesRichardLastWeek, "Thu")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, timeZoneGoalSocialRichardAfterUpdate, expectedValuesRichardLastWeek, "Fri")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, timeZoneGoalSocialRichardAfterUpdate, expectedValuesRichardLastWeek, "Sat")

		assertDayOverviewBasics(responseDayOverviewsAll, expectedTotalDays, expectedTotalDays, 21)
		assertDayOverviewForBudgetGoal(responseDayOverviewsAll, budgetGoalNewsRichardBeforeUpdate, expectedValuesRichardWeekBeforeLastWeek, 2, "Mon")
		assertDayOverviewForBudgetGoal(responseDayOverviewsAll, budgetGoalNewsRichardBeforeUpdate, expectedValuesRichardWeekBeforeLastWeek, 2, "Tue")
		assertDayOverviewForBudgetGoal(responseDayOverviewsAll, budgetGoalNewsRichardBeforeUpdate, expectedValuesRichardWeekBeforeLastWeek, 2, "Wed")
		assertDayOverviewForBudgetGoal(responseDayOverviewsAll, budgetGoalNewsRichardBeforeUpdate, expectedValuesRichardWeekBeforeLastWeek, 2, "Thu")
		assertDayOverviewForBudgetGoal(responseDayOverviewsAll, budgetGoalNewsRichardBeforeUpdate, expectedValuesRichardWeekBeforeLastWeek, 2, "Fri")
		assertDayOverviewForBudgetGoal(responseDayOverviewsAll, budgetGoalNewsRichardBeforeUpdate, expectedValuesRichardWeekBeforeLastWeek, 2, "Sat")

		assertDayOverviewForBudgetGoal(responseDayOverviewsAll, budgetGoalNewsRichardBeforeUpdate, expectedValuesRichardLastWeek, 1, "Sun")
		assertDayOverviewForBudgetGoal(responseDayOverviewsAll, budgetGoalNewsRichardBeforeUpdate, expectedValuesRichardLastWeek, 1, "Mon")
		assertDayOverviewForBudgetGoal(responseDayOverviewsAll, budgetGoalNewsRichardBeforeUpdate, expectedValuesRichardLastWeek, 1, "Tue")
		assertDayOverviewForBudgetGoal(responseDayOverviewsAll, budgetGoalNewsRichardAfterUpdate, expectedValuesRichardLastWeek, 1, "Wed")
		assertDayOverviewForBudgetGoal(responseDayOverviewsAll, budgetGoalNewsRichardAfterUpdate, expectedValuesRichardLastWeek, 1, "Thu")
		assertDayOverviewForBudgetGoal(responseDayOverviewsAll, budgetGoalNewsRichardAfterUpdate, expectedValuesRichardLastWeek, 1, "Fri")
		assertDayOverviewForBudgetGoal(responseDayOverviewsAll, budgetGoalNewsRichardAfterUpdate, expectedValuesRichardLastWeek, 1, "Sat")

		assertDayOverviewForTimeZoneGoal(responseDayOverviewsAll, timeZoneGoalSocialRichardBeforeUpdate, expectedValuesRichardLastWeek, 1, "Thu")
		assertDayOverviewForTimeZoneGoal(responseDayOverviewsAll, timeZoneGoalSocialRichardAfterUpdate, expectedValuesRichardLastWeek, 1, "Fri")
		assertDayOverviewForTimeZoneGoal(responseDayOverviewsAll, timeZoneGoalSocialRichardAfterUpdate, expectedValuesRichardLastWeek, 1, "Sat")

		assertDayDetail(richard, responseDayOverviewsAll, budgetGoalNewsRichardBeforeUpdate, expectedValuesRichardWeekBeforeLastWeek, 2, "Mon")
		assertDayDetail(richard, responseDayOverviewsAll, budgetGoalNewsRichardBeforeUpdate, expectedValuesRichardWeekBeforeLastWeek, 2, "Tue")
		assertDayDetail(richard, responseDayOverviewsAll, budgetGoalNewsRichardBeforeUpdate, expectedValuesRichardWeekBeforeLastWeek, 2, "Wed")
		assertDayDetail(richard, responseDayOverviewsAll, budgetGoalNewsRichardBeforeUpdate, expectedValuesRichardWeekBeforeLastWeek, 2, "Thu")
		assertDayDetail(richard, responseDayOverviewsAll, budgetGoalNewsRichardBeforeUpdate, expectedValuesRichardWeekBeforeLastWeek, 2, "Fri")
		assertDayDetail(richard, responseDayOverviewsAll, budgetGoalNewsRichardBeforeUpdate, expectedValuesRichardWeekBeforeLastWeek, 2, "Sat")

		assertDayDetail(richard, responseDayOverviewsAll, budgetGoalNewsRichardBeforeUpdate, expectedValuesRichardLastWeek, 1, "Mon")
		assertDayDetail(richard, responseDayOverviewsAll, budgetGoalNewsRichardBeforeUpdate, expectedValuesRichardLastWeek, 1, "Tue")
		assertDayDetail(richard, responseDayOverviewsAll, budgetGoalNewsRichardAfterUpdate, expectedValuesRichardLastWeek, 1, "Wed")
		assertDayDetail(richard, responseDayOverviewsAll, budgetGoalNewsRichardAfterUpdate, expectedValuesRichardLastWeek, 1, "Thu")
		assertDayDetail(richard, responseDayOverviewsAll, budgetGoalNewsRichardAfterUpdate, expectedValuesRichardLastWeek, 1, "Fri")
		assertDayDetail(richard, responseDayOverviewsAll, budgetGoalNewsRichardAfterUpdate, expectedValuesRichardLastWeek, 1, "Sat")

		assertDayDetail(richard, responseDayOverviewsAll, timeZoneGoalSocialRichardBeforeUpdate, expectedValuesRichardLastWeek, 1, "Thu")
		assertDayDetail(richard, responseDayOverviewsAll, timeZoneGoalSocialRichardAfterUpdate, expectedValuesRichardLastWeek, 1, "Fri")
		assertDayDetail(richard, responseDayOverviewsAll, timeZoneGoalSocialRichardAfterUpdate, expectedValuesRichardLastWeek, 1, "Sat")

		assertDayOverviewWithBuddiesBasics(responseDayOverviewsWithBuddies, expectedTotalDays, expectedTotalDays, 21)
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, NEWS_ACT_CAT_URL, expectedValuesWithBuddiesWeekBeforeLastWeek, 2, "Mon")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, NEWS_ACT_CAT_URL, expectedValuesWithBuddiesWeekBeforeLastWeek, 2, "Tue")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, NEWS_ACT_CAT_URL, expectedValuesWithBuddiesWeekBeforeLastWeek, 2, "Wed")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, NEWS_ACT_CAT_URL, expectedValuesWithBuddiesWeekBeforeLastWeek, 2, "Thu")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, NEWS_ACT_CAT_URL, expectedValuesWithBuddiesWeekBeforeLastWeek, 2, "Fri")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, NEWS_ACT_CAT_URL, expectedValuesWithBuddiesWeekBeforeLastWeek, 2, "Sat")

		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, SOCIAL_ACT_CAT_URL, expectedValuesWithBuddiesWeekBeforeLastWeek, 2, "Thu")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, SOCIAL_ACT_CAT_URL, expectedValuesWithBuddiesWeekBeforeLastWeek, 2, "Fri")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, SOCIAL_ACT_CAT_URL, expectedValuesWithBuddiesWeekBeforeLastWeek, 2, "Sat")

		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, MULTIMEDIA_ACT_CAT_URL, expectedValuesWithBuddiesWeekBeforeLastWeek, 2, "Thu")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, MULTIMEDIA_ACT_CAT_URL, expectedValuesWithBuddiesWeekBeforeLastWeek, 2, "Fri")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, MULTIMEDIA_ACT_CAT_URL, expectedValuesWithBuddiesWeekBeforeLastWeek, 2, "Sat")

		def weekOverviewBuddyWeekBeforeLastWeek = responseBuddyWeekOverviews.responseData._embedded."yona:weekActivityOverviews"[2]
		assertNumberOfReportedDaysForGoalInWeekOverview(weekOverviewBuddyWeekBeforeLastWeek, budgetGoalSocialBobBeforeUpdate, 3)
		assertDayInWeekOverviewForGoal(weekOverviewBuddyWeekBeforeLastWeek, budgetGoalSocialBobBeforeUpdate, expectedValuesBobWeekBeforeLastWeek, "Thu")
		assertDayInWeekOverviewForGoal(weekOverviewBuddyWeekBeforeLastWeek, budgetGoalSocialBobBeforeUpdate, expectedValuesBobWeekBeforeLastWeek, "Fri")
		assertDayInWeekOverviewForGoal(weekOverviewBuddyWeekBeforeLastWeek, budgetGoalSocialBobBeforeUpdate, expectedValuesBobWeekBeforeLastWeek, "Sat")

		assertWeekDetailForGoal(richard, weekOverviewBuddyWeekBeforeLastWeek, budgetGoalSocialBobBeforeUpdate, expectedValuesBobWeekBeforeLastWeek)

		assertNumberOfReportedDaysForGoalInWeekOverview(weekOverviewBuddyWeekBeforeLastWeek, timeZoneGoalMultimediaBobBeforeUpdate, 2)
		assertDayInWeekOverviewForGoal(weekOverviewBuddyWeekBeforeLastWeek, timeZoneGoalMultimediaBobBeforeUpdate, expectedValuesBobWeekBeforeLastWeek, "Fri")
		assertDayInWeekOverviewForGoal(weekOverviewBuddyWeekBeforeLastWeek, timeZoneGoalMultimediaBobBeforeUpdate, expectedValuesBobWeekBeforeLastWeek, "Sat")

		assertDayDetail(richard, responseBuddyDayOverviews, budgetGoalSocialBobBeforeUpdate, expectedValuesBobWeekBeforeLastWeek, 2, "Thu")
		assertDayDetail(richard, responseBuddyDayOverviews, budgetGoalSocialBobBeforeUpdate, expectedValuesBobWeekBeforeLastWeek, 2, "Fri")
		assertDayDetail(richard, responseBuddyDayOverviews, budgetGoalSocialBobBeforeUpdate, expectedValuesBobWeekBeforeLastWeek, 2, "Sat")

		assertDayDetail(richard, responseBuddyDayOverviews, timeZoneGoalMultimediaBobBeforeUpdate, expectedValuesBobWeekBeforeLastWeek, 2, "Fri")
		assertDayDetail(richard, responseBuddyDayOverviews, timeZoneGoalMultimediaBobBeforeUpdate, expectedValuesBobWeekBeforeLastWeek, 2, "Sat")

		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, NEWS_ACT_CAT_URL, expectedValuesWithBuddiesLastWeek, 1, "Sun")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, NEWS_ACT_CAT_URL, expectedValuesWithBuddiesLastWeek, 1, "Mon")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, NEWS_ACT_CAT_URL, expectedValuesWithBuddiesLastWeek, 1, "Tue")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, NEWS_ACT_CAT_URL, expectedValuesWithBuddiesLastWeek, 1, "Wed")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, NEWS_ACT_CAT_URL, expectedValuesWithBuddiesLastWeek, 1, "Thu")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, NEWS_ACT_CAT_URL, expectedValuesWithBuddiesLastWeek, 1, "Fri")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, NEWS_ACT_CAT_URL, expectedValuesWithBuddiesLastWeek, 1, "Sat")

		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, SOCIAL_ACT_CAT_URL, expectedValuesWithBuddiesLastWeek, 1, "Sun")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, SOCIAL_ACT_CAT_URL, expectedValuesWithBuddiesLastWeek, 1, "Mon")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, SOCIAL_ACT_CAT_URL, expectedValuesWithBuddiesLastWeek, 1, "Tue")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, SOCIAL_ACT_CAT_URL, expectedValuesWithBuddiesLastWeek, 1, "Wed")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, SOCIAL_ACT_CAT_URL, expectedValuesWithBuddiesLastWeek, 1, "Thu")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, SOCIAL_ACT_CAT_URL, expectedValuesWithBuddiesLastWeek, 1, "Fri")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, SOCIAL_ACT_CAT_URL, expectedValuesWithBuddiesLastWeek, 1, "Sat")

		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, MULTIMEDIA_ACT_CAT_URL, expectedValuesWithBuddiesLastWeek, 1, "Sun")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, MULTIMEDIA_ACT_CAT_URL, expectedValuesWithBuddiesLastWeek, 1, "Mon")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, MULTIMEDIA_ACT_CAT_URL, expectedValuesWithBuddiesLastWeek, 1, "Tue")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, MULTIMEDIA_ACT_CAT_URL, expectedValuesWithBuddiesLastWeek, 1, "Wed")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, MULTIMEDIA_ACT_CAT_URL, expectedValuesWithBuddiesLastWeek, 1, "Thu")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, MULTIMEDIA_ACT_CAT_URL, expectedValuesWithBuddiesLastWeek, 1, "Fri")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, MULTIMEDIA_ACT_CAT_URL, expectedValuesWithBuddiesLastWeek, 1, "Sat")

		def weekOverviewBuddyLastWeek = responseBuddyWeekOverviews.responseData._embedded."yona:weekActivityOverviews"[1]
		assertNumberOfReportedDaysForGoalInWeekOverview(weekOverviewBuddyLastWeek, budgetGoalSocialBobAfterUpdate, 7)
		assertDayInWeekOverviewForGoal(weekOverviewBuddyLastWeek, budgetGoalSocialBobBeforeUpdate, expectedValuesBobLastWeek, "Sun")
		assertDayInWeekOverviewForGoal(weekOverviewBuddyLastWeek, budgetGoalSocialBobBeforeUpdate, expectedValuesBobLastWeek, "Mon")
		assertDayInWeekOverviewForGoal(weekOverviewBuddyLastWeek, budgetGoalSocialBobBeforeUpdate, expectedValuesBobLastWeek, "Tue")
		assertDayInWeekOverviewForGoal(weekOverviewBuddyLastWeek, budgetGoalSocialBobBeforeUpdate, expectedValuesBobLastWeek, "Wed")
		assertDayInWeekOverviewForGoal(weekOverviewBuddyLastWeek, budgetGoalSocialBobBeforeUpdate, expectedValuesBobLastWeek, "Thu")
		assertDayInWeekOverviewForGoal(weekOverviewBuddyLastWeek, budgetGoalSocialBobBeforeUpdate, expectedValuesBobLastWeek, "Fri")
		assertDayInWeekOverviewForGoal(weekOverviewBuddyLastWeek, budgetGoalSocialBobAfterUpdate, expectedValuesBobLastWeek, "Sat")

		assertWeekDetailForGoal(richard, weekOverviewBuddyLastWeek, budgetGoalSocialBobAfterUpdate, expectedValuesBobLastWeek)

		assertNumberOfReportedDaysForGoalInWeekOverview(weekOverviewBuddyLastWeek, timeZoneGoalMultimediaBobAfterUpdate, 7)
		assertDayInWeekOverviewForGoal(weekOverviewBuddyLastWeek, timeZoneGoalMultimediaBobBeforeUpdate, expectedValuesBobLastWeek, "Sun")
		assertDayInWeekOverviewForGoal(weekOverviewBuddyLastWeek, timeZoneGoalMultimediaBobBeforeUpdate, expectedValuesBobLastWeek, "Mon")
		assertDayInWeekOverviewForGoal(weekOverviewBuddyLastWeek, timeZoneGoalMultimediaBobBeforeUpdate, expectedValuesBobLastWeek, "Tue")
		assertDayInWeekOverviewForGoal(weekOverviewBuddyLastWeek, timeZoneGoalMultimediaBobBeforeUpdate, expectedValuesBobLastWeek, "Wed")
		assertDayInWeekOverviewForGoal(weekOverviewBuddyLastWeek, timeZoneGoalMultimediaBobBeforeUpdate, expectedValuesBobLastWeek, "Thu")
		assertDayInWeekOverviewForGoal(weekOverviewBuddyLastWeek, timeZoneGoalMultimediaBobAfterUpdate, expectedValuesBobLastWeek, "Fri")
		assertDayInWeekOverviewForGoal(weekOverviewBuddyLastWeek, timeZoneGoalMultimediaBobAfterUpdate, expectedValuesBobLastWeek, "Sat")

		assertDayDetail(richard, responseBuddyDayOverviews, budgetGoalSocialBobBeforeUpdate, expectedValuesBobLastWeek, 1, "Sun")
		assertDayDetail(richard, responseBuddyDayOverviews, budgetGoalSocialBobBeforeUpdate, expectedValuesBobLastWeek, 1, "Mon")
		assertDayDetail(richard, responseBuddyDayOverviews, budgetGoalSocialBobBeforeUpdate, expectedValuesBobLastWeek, 1, "Tue")
		assertDayDetail(richard, responseBuddyDayOverviews, budgetGoalSocialBobBeforeUpdate, expectedValuesBobLastWeek, 1, "Wed")
		assertDayDetail(richard, responseBuddyDayOverviews, budgetGoalSocialBobBeforeUpdate, expectedValuesBobLastWeek, 1, "Thu")
		assertDayDetail(richard, responseBuddyDayOverviews, budgetGoalSocialBobBeforeUpdate, expectedValuesBobLastWeek, 1, "Fri")
		assertDayDetail(richard, responseBuddyDayOverviews, budgetGoalSocialBobAfterUpdate, expectedValuesBobLastWeek, 1, "Sat")

		assertDayDetail(richard, responseBuddyDayOverviews, timeZoneGoalMultimediaBobBeforeUpdate, expectedValuesBobLastWeek, 1, "Sun")
		assertDayDetail(richard, responseBuddyDayOverviews, timeZoneGoalMultimediaBobBeforeUpdate, expectedValuesBobLastWeek, 1, "Mon")
		assertDayDetail(richard, responseBuddyDayOverviews, timeZoneGoalMultimediaBobBeforeUpdate, expectedValuesBobLastWeek, 1, "Tue")
		assertDayDetail(richard, responseBuddyDayOverviews, timeZoneGoalMultimediaBobBeforeUpdate, expectedValuesBobLastWeek, 1, "Wed")
		assertDayDetail(richard, responseBuddyDayOverviews, timeZoneGoalMultimediaBobBeforeUpdate, expectedValuesBobLastWeek, 1, "Thu")
		assertDayDetail(richard, responseBuddyDayOverviews, timeZoneGoalMultimediaBobAfterUpdate, expectedValuesBobLastWeek, 1, "Fri")
		assertDayDetail(richard, responseBuddyDayOverviews, timeZoneGoalMultimediaBobAfterUpdate, expectedValuesBobLastWeek, 1, "Sat")
		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Comment on buddy day activity'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob

		when:
		setGoalCreationTime(bob, NEWS_ACT_CAT_URL, "W-1 Mon 02:18")

		richard = appService.reloadUser(richard)
		bob = appService.reloadUser(bob)

		then:
		assertCommentingWorks(richard, bob, false, {user -> appService.getDayActivityOverviews(user, ["size": 14])},
		{user -> appService.getDayActivityOverviews(user, user.buddies[0], ["size": 14])},
		{responseOverviews, user, goal -> getDayDetails(responseOverviews, user, goal, 1, "Tue")})

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
		setGoalCreationTime(bob, NEWS_ACT_CAT_URL, "W-1 Mon 02:18")

		richard = appService.reloadUser(richard)
		bob = appService.reloadUser(bob)

		then:
		assertCommentingWorks(richard, bob, true, {user -> appService.getWeekActivityOverviews(user, ["size": 14])},
		{user -> appService.getWeekActivityOverviews(user, user.buddies[0], ["size": 14])},
		{responseOverviews, user, goal -> getWeekDetails(responseOverviews, user, goal, 1)})

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
		setGoalCreationTime(richard, NEWS_ACT_CAT_URL, "W-1 Mon 02:18")
		richard = appService.reloadUser(richard)
		bob = appService.reloadUser(bob)
		bea = appService.reloadUser(bea)
		Goal richardGoal = richard.findActiveGoal(NEWS_ACT_CAT_URL)
		Goal bobGoalBuddyRichard = bob.buddies[0].findActiveGoal(NEWS_ACT_CAT_URL)
		Goal beaGoalBuddyRichard = bea.buddies[0].findActiveGoal(NEWS_ACT_CAT_URL)
		def bobResponseOverviewsRichardAsBuddyAll = appService.getDayActivityOverviews(bob, bob.buddies[0], ["size": 14])
		def bobResponseDetailsRichardAsBuddy = getDayDetails(bobResponseOverviewsRichardAsBuddyAll, bob, bobGoalBuddyRichard, 1, "Tue")
		def beaResponseOverviewsRichardAsBuddyAll = appService.getDayActivityOverviews(bea, bea.buddies[0], ["size": 14])
		def beaResponseDetailsRichardAsBuddy = getDayDetails(beaResponseOverviewsRichardAsBuddyAll, bea, beaGoalBuddyRichard, 1, "Tue")
		def richardResponseOverviewsAll = appService.getDayActivityOverviews(richard, ["size": 14])
		def richardResponseDetails = getDayDetails(richardResponseOverviewsAll, richard, richardGoal, 1, "Tue")

		when:
		def messageBob1 = appService.yonaServer.createResourceWithPassword(bobResponseDetailsRichardAsBuddy.responseData._links."yona:addComment".href, """{"message": "Hi buddy! How ya doing?"}""", bob.password)
		def messageBea1 = appService.yonaServer.createResourceWithPassword(beaResponseDetailsRichardAsBuddy.responseData._links."yona:addComment".href, """{"message": "Hi Richard! Everything alright?"}""", bea.password)

		def richardMessages = getActivityDetailMessages(richardResponseDetails, richard, 2).responseData._embedded."yona:messages"
		def messageBob1AsSeenByRichard = richardMessages[0]
		assert messageBob1AsSeenByRichard.nickname == "BD"
		def messageBea1AsSeenByRichard = richardMessages[1]
		assert messageBea1AsSeenByRichard.nickname == "BDD"
		def messageRichardReplyToBob = appService.postMessageActionWithPassword(messageBob1AsSeenByRichard._links."yona:reply".href, ["message" : "Hi Bob! Doing fine!"], richard.password)

		def bobMessagesRichard = getActivityDetailMessages(bobResponseDetailsRichardAsBuddy, bob, 2).responseData._embedded."yona:messages"
		def beaMessagesRichard = getActivityDetailMessages(beaResponseDetailsRichardAsBuddy, bea, 1).responseData._embedded."yona:messages"

		then:
		bobMessagesRichard[0].nickname == "BD"
		bobMessagesRichard[1].nickname == "RQ"

		beaMessagesRichard[0].nickname == "BDD"

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
		setGoalCreationTime(richard, NEWS_ACT_CAT_URL, "W-1 Mon 02:18")
		richard = appService.reloadUser(richard)
		bob = appService.reloadUser(bob)
		bea = appService.reloadUser(bea)
		Goal richardGoal = richard.findActiveGoal(NEWS_ACT_CAT_URL)
		Goal bobGoalBuddyRichard = bob.buddies[0].findActiveGoal(NEWS_ACT_CAT_URL)
		Goal beaGoalBuddyRichard = bea.buddies[0].findActiveGoal(NEWS_ACT_CAT_URL)
		def bobResponseOverviewsRichardAsBuddyAll = appService.getDayActivityOverviews(bob, bob.buddies[0], ["size": 14])
		def bobResponseDetailsRichardAsBuddy = getDayDetails(bobResponseOverviewsRichardAsBuddyAll, bob, bobGoalBuddyRichard, 1, "Tue")
		def beaResponseOverviewsRichardAsBuddyAll = appService.getDayActivityOverviews(bea, bea.buddies[0], ["size": 14])
		def beaResponseDetailsRichardAsBuddy = getDayDetails(beaResponseOverviewsRichardAsBuddyAll, bea, beaGoalBuddyRichard, 1, "Tue")
		def richardResponseOverviewsAll = appService.getDayActivityOverviews(richard, ["size": 14])
		def richardResponseDetails = getDayDetails(richardResponseOverviewsAll, richard, richardGoal, 1, "Tue")

		when:
		def messageBob1 = appService.yonaServer.createResourceWithPassword(bobResponseDetailsRichardAsBuddy.responseData._links."yona:addComment".href, """{"message": "Hi buddy! How ya doing?"}""", bob.password)
		def messageBea1 = appService.yonaServer.createResourceWithPassword(beaResponseDetailsRichardAsBuddy.responseData._links."yona:addComment".href, """{"message": "Hi Richard! Everything alright?"}""", bea.password)

		def richardMessages = getActivityDetailMessages(richardResponseDetails, richard, 2).responseData._embedded."yona:messages"
		def messageBob1AsSeenByRichard = richardMessages[0]
		assert messageBob1AsSeenByRichard.nickname == "BD"
		def messageBea1AsSeenByRichard = richardMessages[1]
		assert messageBea1AsSeenByRichard.nickname == "BDD"
		def messageRichardReplyToBea = appService.postMessageActionWithPassword(messageBea1AsSeenByRichard._links."yona:reply".href, ["message" : "Hi Bea! I'm alright!"], richard.password)
		def messageRichardReplyToBob = appService.postMessageActionWithPassword(messageBob1AsSeenByRichard._links."yona:reply".href, ["message" : "Hi Bob! Doing fine!"], richard.password)

		def bobMessagesRichard = getActivityDetailMessages(bobResponseDetailsRichardAsBuddy, bob, 2).responseData._embedded."yona:messages"
		def beaMessagesRichard = getActivityDetailMessages(beaResponseDetailsRichardAsBuddy, bea, 2).responseData._embedded."yona:messages"
		def messageBobReplyToRichardAgain = appService.postMessageActionWithPassword(bobMessagesRichard[1]._links."yona:reply".href, ["message" : "Great buddy!"], bob.password)

		def richardMessagesRevisited = getActivityDetailMessages(richardResponseDetails, richard, 5, 10).responseData._embedded."yona:messages"

		then:
		richardMessagesRevisited[0].nickname == "BD"
		richardMessagesRevisited[1].nickname == "RQ"
		richardMessagesRevisited[2].nickname == "BD"
		richardMessagesRevisited[3].nickname == "BDD"
		richardMessagesRevisited[4].nickname == "RQ"

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
		appService.deleteUser(bea)
	}

	def 'Richard retrieves buddy activity info before Bob accepted Richard\'s buddy request'()
	{
		given:
		User richard = addRichard()
		User bob = addBob()
		appService.sendBuddyConnectRequest(richard, bob)
		richard = appService.reloadUser(richard)

		when:
		def responseDayOverviewsWithBuddies = appService.getDayActivityOverviewsWithBuddies(richard, ["size": 14])

		then:
		richard.buddies[0].dailyActivityReportsUrl == null
		richard.buddies[0].weeklyActivityReportsUrl == null
		responseDayOverviewsWithBuddies.status == 200
		responseDayOverviewsWithBuddies.responseData._embedded."yona:dayActivityOverviews"[0].dayActivities.find{ it._links."yona:activityCategory"?.href == GAMBLING_ACT_CAT_URL}.dayActivitiesForUsers.size() == 1

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Richard retrieves buddy activity info before he processed Bob\'s acceptance'()
	{
		given:
		User richard = addRichard()
		User bob = addBob()
		appService.sendBuddyConnectRequest(richard, bob)
		def connectRequestMessage = appService.fetchBuddyConnectRequestMessage(bob)
		def acceptURL = connectRequestMessage.acceptURL
		assert appService.postMessageActionWithPassword(acceptURL, ["message" : "Yes, great idea!"], bob.password).status == 200
		richard = appService.reloadUser(richard)

		when:
		def responseDayOverviewsWithBuddies = appService.getDayActivityOverviewsWithBuddies(richard, ["size": 14])

		then:
		richard.buddies[0].dailyActivityReportsUrl == null
		richard.buddies[0].weeklyActivityReportsUrl == null
		responseDayOverviewsWithBuddies.status == 200
		responseDayOverviewsWithBuddies.responseData._embedded."yona:dayActivityOverviews"[0].dayActivities.find{ it._links."yona:activityCategory"?.href == GAMBLING_ACT_CAT_URL}.dayActivitiesForUsers.size() == 1

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Bob retrieves buddy activity info before Richard processed Bob\'s acceptance'()
	{
		given:
		User richard = addRichard()
		User bob = addBob()
		appService.sendBuddyConnectRequest(richard, bob)
		def connectRequestMessage = appService.fetchBuddyConnectRequestMessage(bob)
		def acceptURL = connectRequestMessage.acceptURL
		assert appService.postMessageActionWithPassword(acceptURL, ["message" : "Yes, great idea!"], bob.password).status == 200
		richard = appService.reloadUser(richard)
		bob = appService.reloadUser(bob)
		Goal budgetGoalGamblingRichard = bob.buddies[0].findActiveGoal(GAMBLING_ACT_CAT_URL)

		when:
		def responseDayOverviewsWithBuddies = appService.getDayActivityOverviewsWithBuddies(bob, ["size": 14])

		then:
		responseDayOverviewsWithBuddies.status == 200
		responseDayOverviewsWithBuddies.responseData._embedded."yona:dayActivityOverviews"[0].dayActivities.find{ it._links."yona:activityCategory"?.href == GAMBLING_ACT_CAT_URL}.dayActivitiesForUsers.size() == 2
		def responseWeekOverviews = appService.getWeekActivityOverviews(bob, bob.buddies[0])
		responseWeekOverviews.status == 200
		responseWeekOverviews.responseData._embedded."yona:weekActivityOverviews"[0].weekActivities.find{ it._links."yona:goal".href == budgetGoalGamblingRichard.url}
		def weekActivityForGoal = responseWeekOverviews.responseData._embedded."yona:weekActivityOverviews"[0].weekActivities.find{ it._links."yona:goal".href == budgetGoalGamblingRichard.url}
		def dayActivityInWeekForGoal = weekActivityForGoal.dayActivities[YonaServer.now.dayOfWeek.toString()]
		dayActivityInWeekForGoal.totalActivityDurationMinutes == 0

		def responseDayOverviews = appService.getDayActivityOverviews(bob, bob.buddies[0])

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Richard retrieves buddy activity info before he processed Bob\'s disconnect'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob
		def responseRemoveBuddy = appService.removeBuddy(bob, appService.getBuddies(bob)[0], "Sorry, I regret having asked you")
		assert responseRemoveBuddy.status == 200

		when:
		def responseDayOverviewsWithBuddies = appService.getDayActivityOverviewsWithBuddies(richard, ["size": 14])

		then:
		responseDayOverviewsWithBuddies.status == 200
		responseDayOverviewsWithBuddies.responseData._embedded."yona:dayActivityOverviews"[0].dayActivities.find{ it._links."yona:activityCategory"?.href == GAMBLING_ACT_CAT_URL}.dayActivitiesForUsers.size() == 1

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	private void assertCommentingWorks(User richard, User bob, boolean isWeek, Closure userOverviewRetriever, Closure buddyOverviewRetriever, Closure detailsRetriever)
	{
		Goal budgetGoalNewsBuddyBob = richard.buddies[0].findActiveGoal(NEWS_ACT_CAT_URL)
		Goal budgetGoalNewsBob = bob.findActiveGoal(NEWS_ACT_CAT_URL)

		def responseOverviewsBobAsBuddyAll = buddyOverviewRetriever(richard)
		assert responseOverviewsBobAsBuddyAll.status == 200

		def responseDetailsBobAsBuddy = detailsRetriever(responseOverviewsBobAsBuddyAll, richard, budgetGoalNewsBuddyBob)
		assert responseDetailsBobAsBuddy.responseData._links."yona:addComment".href
		assert responseDetailsBobAsBuddy.responseData._links."yona:messages".href

		def message = """{"message": "You're quiet!"}"""
		def responseAddMessage = appService.yonaServer.createResourceWithPassword(responseDetailsBobAsBuddy.responseData._links."yona:addComment".href, message, richard.password)

		assert responseAddMessage.status == 200
		def addedMessage = responseAddMessage.responseData
		assertCommentMessageDetails(addedMessage, richard, isWeek, richard, responseDetailsBobAsBuddy.responseData._links.self.href, "You're quiet!", addedMessage)

		assertMarkReadUnread(richard, addedMessage)

		def responseInitialGetCommentMessagesSeenByRichard = getActivityDetailMessages(responseDetailsBobAsBuddy, richard, 1)
		def initialMessagesSeenByRichard = responseInitialGetCommentMessagesSeenByRichard.responseData._embedded."yona:messages"
		assertCommentMessageDetails(initialMessagesSeenByRichard[0], richard, isWeek, richard, responseDetailsBobAsBuddy.responseData._links.self.href, "You're quiet!", initialMessagesSeenByRichard[0])

		def responseOverviewsBobAll = userOverviewRetriever(bob)
		assert responseOverviewsBobAll.status == 200

		def responseDetailsBob = detailsRetriever(responseOverviewsBobAll, bob, budgetGoalNewsBob)
		assert responseDetailsBob.responseData._links."yona:addComment" == null
		assert responseDetailsBob.responseData._links."yona:messages".href

		def responseInitialGetCommentMessagesSeenByBob = getActivityDetailMessages(responseDetailsBob, bob, 1)
		def initialMessagesSeenByBob = responseInitialGetCommentMessagesSeenByBob.responseData._embedded."yona:messages"
		assertCommentMessageDetails(initialMessagesSeenByBob[0], bob, isWeek, bob.buddies[0], responseDetailsBob.responseData._links.self.href, "You're quiet!", initialMessagesSeenByBob[0])

		replyToMessage(initialMessagesSeenByBob[0], bob, "My battery died :)", isWeek, responseDetailsBob, initialMessagesSeenByBob[0])

		def responseSecondGetCommentMessagesSeenByRichard = getActivityDetailMessages(responseDetailsBobAsBuddy, richard, 2)
		def replyMessagesSeenByRichard = responseSecondGetCommentMessagesSeenByRichard.responseData._embedded."yona:messages"
		assertCommentMessageDetails(replyMessagesSeenByRichard[1], richard, isWeek, richard.buddies[0], responseDetailsBobAsBuddy.responseData._links.self.href, "My battery died :)",
				replyMessagesSeenByRichard[0], responseSecondGetCommentMessagesSeenByRichard.responseData._embedded."yona:messages"[0])

		replyToMessage(replyMessagesSeenByRichard[1], richard, "Too bad!", isWeek, responseDetailsBobAsBuddy, replyMessagesSeenByRichard[0])

		def responseSecondGetCommentMessagesSeenByBob = getActivityDetailMessages(responseDetailsBob, bob, 3)
		def secondReplyMessagesSeenByBob = responseSecondGetCommentMessagesSeenByBob.responseData._embedded."yona:messages"
		assertCommentMessageDetails(secondReplyMessagesSeenByBob[2], bob, isWeek, bob.buddies[0], responseDetailsBob.responseData._links.self.href, "Too bad!", secondReplyMessagesSeenByBob[0])

		replyToMessage(secondReplyMessagesSeenByBob[2], bob, "Yes, it is...", isWeek, responseDetailsBob, secondReplyMessagesSeenByBob[0])

		def responseThirdGetCommentMessagesSeenByRichard = getActivityDetailMessages(responseDetailsBobAsBuddy, richard, 4)
		def replyToReplyMessagesSeenByRichard = responseThirdGetCommentMessagesSeenByRichard.responseData._embedded."yona:messages"
		assertCommentMessageDetails(replyToReplyMessagesSeenByRichard[3], richard, isWeek, richard.buddies[0], responseDetailsBobAsBuddy.responseData._links.self.href, "Yes, it is...", replyToReplyMessagesSeenByRichard[0])

		replyToMessage(replyToReplyMessagesSeenByRichard[3], richard, "No budget for a new one?", isWeek, responseDetailsBobAsBuddy, replyToReplyMessagesSeenByRichard[0])

		def responseFinalGetCommentMessagesSeenByRichard = getActivityDetailMessages(responseDetailsBobAsBuddy, richard, 5)
		def messagesRichard = responseFinalGetCommentMessagesSeenByRichard.responseData._embedded."yona:messages"
		assertCommentMessageDetails(messagesRichard[0], richard, isWeek, richard, responseDetailsBobAsBuddy.responseData._links.self.href, "You're quiet!", messagesRichard[0])
		assertCommentMessageDetails(messagesRichard[1], richard, isWeek, richard.buddies[0], responseDetailsBobAsBuddy.responseData._links.self.href, "My battery died :)", messagesRichard[0], messagesRichard[0])
		assertCommentMessageDetails(messagesRichard[2], richard, isWeek, richard, responseDetailsBobAsBuddy.responseData._links.self.href, "Too bad!", messagesRichard[0], messagesRichard[1])
		assertCommentMessageDetails(messagesRichard[3], richard, isWeek, richard.buddies[0], responseDetailsBobAsBuddy.responseData._links.self.href, "Yes, it is...", messagesRichard[0], messagesRichard[2])
		assertNextPage(responseFinalGetCommentMessagesSeenByRichard, richard)

		def responseFinalGetCommentMessagesSeenByBob = getActivityDetailMessages(responseDetailsBob, bob, 5)
		def messagesBob = responseFinalGetCommentMessagesSeenByBob.responseData._embedded."yona:messages"
		assertCommentMessageDetails(messagesBob[0], bob, isWeek, bob.buddies[0], responseDetailsBob.responseData._links.self.href, "You're quiet!", messagesBob[0])
		assertCommentMessageDetails(messagesBob[1], bob, isWeek, bob, responseDetailsBob.responseData._links.self.href, "My battery died :)", messagesBob[0], messagesBob[0])
		assertCommentMessageDetails(messagesBob[2], bob, isWeek, bob.buddies[0], responseDetailsBob.responseData._links.self.href, "Too bad!", messagesBob[0], messagesBob[1])
		assertCommentMessageDetails(messagesBob[3], bob, isWeek, bob, responseDetailsBob.responseData._links.self.href, "Yes, it is...", messagesBob[0], messagesBob[2])
		assertNextPage(responseFinalGetCommentMessagesSeenByBob, bob)

		def allMessagesRichardResponse = appService.getMessages(richard)
		assert allMessagesRichardResponse.responseData._embedded?."yona:messages".findAll{ it."@type" == "ActivityCommentMessage"}.size() == 2
		def activityCommentMessagesRichard = allMessagesRichardResponse.responseData._embedded?."yona:messages".findAll{ it."@type" == "ActivityCommentMessage"}
		assertCommentMessageDetails(activityCommentMessagesRichard[0], richard, isWeek, richard.buddies[0], responseDetailsBobAsBuddy.responseData._links.self.href, "Yes, it is...", messagesRichard[0])
		assertCommentMessageDetails(activityCommentMessagesRichard[1], richard, isWeek, richard.buddies[0], responseDetailsBobAsBuddy.responseData._links.self.href, "My battery died :)", messagesRichard[0])

		def allMessagesBobResponse = appService.getMessages(bob)
		assert allMessagesBobResponse.responseData._embedded?."yona:messages".findAll{ it."@type" == "ActivityCommentMessage"}.size() == 3
		def activityCommentMessagesBob = allMessagesBobResponse.responseData._embedded?."yona:messages".findAll{ it."@type" == "ActivityCommentMessage"}
		assertCommentMessageDetails(activityCommentMessagesBob[0], bob, isWeek, bob.buddies[0], responseDetailsBob.responseData._links.self.href, "No budget for a new one?", activityCommentMessagesBob[2])
		assertCommentMessageDetails(activityCommentMessagesBob[1], bob, isWeek, bob.buddies[0], responseDetailsBob.responseData._links.self.href, "Too bad!", activityCommentMessagesBob[2])
		assertCommentMessageDetails(activityCommentMessagesBob[2], bob, isWeek, bob.buddies[0], responseDetailsBob.responseData._links.self.href, "You're quiet!", activityCommentMessagesBob[2])
	}

	private void assertWeekDateForCurrentWeek(responseWeekOverviews)
	{
		// Java treats Sunday as last day of the week while Yona treats it as first.
		// For that reason, we ignore mismatches when they occur on a Sunday.
		boolean isSunday = YonaServer.now.getDayOfWeek() == DayOfWeek.SUNDAY
		DateTimeFormatter weekFormatter = new DateTimeFormatterBuilder()
				.parseCaseInsensitive()
				.appendValue(IsoFields.WEEK_BASED_YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
				.appendLiteral("-W")
				.appendValue(IsoFields.WEEK_OF_WEEK_BASED_YEAR, 2)
				.toFormatter(YonaServer.EN_US_LOCALE)

		String currentWeek = weekFormatter.format(YonaServer.now)
		assert responseWeekOverviews.responseData._embedded."yona:weekActivityOverviews"[0].date == currentWeek || isSunday
	}

	private void replyToMessage(messageToReply, User senderUser, messageToSend, boolean isWeek, responseGetActivityDetails, threadHeadMessage) {
		def responseReplyFromBob = appService.postMessageActionWithPassword(messageToReply._links."yona:reply".href, ["message" : messageToSend], senderUser.password)
		assert responseReplyFromBob.status == 200
		assert responseReplyFromBob.responseData.properties["status"] == "done"
		assert responseReplyFromBob.responseData._embedded?."yona:affectedMessages"?.size() == 1
		def replyMessage = responseReplyFromBob.responseData._embedded."yona:affectedMessages"[0]
		assertCommentMessageDetails(replyMessage, senderUser, isWeek, senderUser, responseGetActivityDetails.responseData._links.self.href, messageToSend, threadHeadMessage, messageToReply)
	}

	private getActivityDetailMessages(responseGetActivityDetails, User user, int expectedNumMessages, int pageSize = 4) {
		int expectedNumMessagesInPage = Math.min(expectedNumMessages, pageSize)
		def response = appService.yonaServer.getResourceWithPassword(responseGetActivityDetails.responseData._links."yona:messages".href, user.password, ["size":pageSize])

		assert response.status == 200
		assert response.responseData?._embedded?."yona:messages"?.size() == expectedNumMessagesInPage
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

	private void assertNextPage(responseGetActivityDetails, User user) {
		int defaultPageSize = 4
		def response = appService.yonaServer.getResourceWithPassword(responseGetActivityDetails.responseData._links.next.href, user.password)

		assert response.status == 200
		assert response.responseData?._embedded?."yona:messages"?.size() == 1
		assert response.responseData.page.size == defaultPageSize
		assert response.responseData.page.totalElements == 5
		assert response.responseData.page.totalPages == 2
		assert response.responseData.page.number == 1

		assert response.responseData._links?.prev?.href
		assert response.responseData._links?.next?.href == null
	}

	private void assertCommentMessageDetails(message, User user, boolean isWeek, sender, expectedDetailsUrl, expectedText, threadHeadMessage, repliedMessage = null) {
		assert message."@type" == "ActivityCommentMessage"
		assert message.message == expectedText
		assert message.nickname == sender.nickname

		assert message._links?.self?.href?.startsWith(YonaServer.stripQueryString(user.messagesUrl))
		assert message._links?.edit?.href == message._links.self.href
		assert message._links?."yona:threadHead"?.href == threadHeadMessage._links.self.href

		if (repliedMessage) {
			assert repliedMessage._links.self.href == message._links?."yona:repliedMessage"?.href
		}

		if (isWeek) {
			assert message._links?."yona:weekDetails"?.href == expectedDetailsUrl
			assert message._links?."yona:dayDetails"?.href == null
		} else {
			assert message._links?."yona:weekDetails"?.href == null
			assert message._links?."yona:dayDetails"?.href == expectedDetailsUrl
		}
		if (sender instanceof Buddy) {
			assert message._links?."yona:buddy"?.href == sender.url
			assert message._links?."yona:reply"?.href.startsWith(user.url)
		} else {
			assert message._links?."yona:user"?.href == sender.url
			assert message._links?."yona:reply"?.href == null
		}
	}

	private void assertWeekDetailPrevNextLinks(User user, weekActivityForGoal, expectedPrevWeekForGoal, expectedNextWeekForGoal)
	{
		def weekDetails = getWeekDetailsForWeek(user, weekActivityForGoal)
		assert weekDetails?.responseData?._links?."prev"?.href == expectedPrevWeekForGoal?._links?."yona:weekDetails"?.href
		assert weekDetails?.responseData?._links?."next"?.href == expectedNextWeekForGoal?._links?."yona:weekDetails"?.href
	}

	private void assertDayDetailPrevNextLinks(User user, dayActivityForGoal, expectedPrevDayForGoal, expectedNextDayForGoal)
	{
		def dayDetails = getDayDetailsForDay(user, dayActivityForGoal)
		assert dayDetails?.responseData?._links?."prev"?.href == expectedPrevDayForGoal?._links?."yona:dayDetails"?.href
		assert dayDetails?.responseData?._links?."next"?.href == expectedNextDayForGoal?._links?."yona:dayDetails"?.href
	}

	private getDayDetails(responseDayOverviewsAll, User user, Goal goal, int weeksBack, String shortDay) {
		def dayOffset = YonaServer.relativeDateStringToDaysOffset(weeksBack, shortDay)
		def dayActivityOverview = responseDayOverviewsAll.responseData._embedded."yona:dayActivityOverviews"[dayOffset]
		def dayActivityForGoal = dayActivityOverview.dayActivities.find{ it._links."yona:goal".href == goal.url}
		return getDayDetailsForDay(user, dayActivityForGoal)
	}

	private getDayDetailsForDay(User user, dayActivityForGoal) {
		assert dayActivityForGoal?._links?."yona:dayDetails"?.href
		def dayActivityDetailUrl =  dayActivityForGoal?._links?."yona:dayDetails"?.href
		def response = appService.getResourceWithPassword(dayActivityDetailUrl, user.password)
		assert response.status == 200
		return response
	}

	private getWeekDetails(responseWeekOverviewsAll, User user, Goal goal, int weeksBack) {
		def weekActivityOverview = responseWeekOverviewsAll.responseData._embedded."yona:weekActivityOverviews"[weeksBack]
		def weekActivityForGoal = weekActivityOverview.weekActivities.find{ it._links."yona:goal".href == goal.url}
		return getWeekDetailsForWeek(user, weekActivityForGoal)
	}

	private getWeekDetailsForWeek(User user, weekActivityForGoal) {
		assert weekActivityForGoal?._links?."yona:weekDetails"?.href
		def weekActivityDetailUrl =  weekActivityForGoal?._links?."yona:weekDetails"?.href
		def response = appService.getResourceWithPassword(weekActivityDetailUrl, user.password)
		assert response.status == 200
		return response
	}
}
