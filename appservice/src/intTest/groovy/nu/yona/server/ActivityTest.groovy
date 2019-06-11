/*******************************************************************************
 * Copyright (c) 2015, 2019 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import static nu.yona.server.test.CommonAssertions.*

import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.SignStyle
import java.time.temporal.IsoFields

import nu.yona.server.test.AppActivity
import nu.yona.server.test.BudgetGoal
import nu.yona.server.test.CommonAssertions
import nu.yona.server.test.Device
import nu.yona.server.test.Goal
import nu.yona.server.test.TimeZoneGoal
import nu.yona.server.test.User

class ActivityTest extends AbstractAppServiceIntegrationTest
{
	def 'Retrieve activity with buddies when no buddies gives empty'()
	{
		given:
		def richard = addRichard()
		setGoalCreationTime(richard, NEWS_ACT_CAT_URL, "W-1 Mon 02:18")
		reportAppActivity(richard, "NU.nl", "W-1 Mon 03:15", "W-1 Mon 03:35")

		when:
		def responseDayOverviewsWithBuddies = appService.getDayActivityOverviewsWithBuddies(richard)

		then:
		//zero results if user has no buddies
		assertDayOverviewWithBuddiesBasics(responseDayOverviewsWithBuddies, 0, 0)

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Retrieve activity reports without activity'()
	{
		given:
		def richard = addRichard()
		setCreationTimeOfMandatoryGoalsToNow(richard)
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
		assertResponseStatusOk(response)

		def dayActivityOverview = responseDayOverviews.responseData._embedded."yona:dayActivityOverviews"[0]
		def dayActivityForGoal = dayActivityOverview.dayActivities.find{ it._links."yona:goal".href == goal.url}
		assert dayActivityForGoal?._links?."yona:dayDetails"?.href
		def dayActivityDetailUrl =  dayActivityForGoal?._links?."yona:dayDetails"?.href
		def responseDayDetail = appService.getResourceWithPassword(dayActivityDetailUrl, richard.password)
		assertResponseStatusOk(responseDayDetail)

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Page through multiple weeks'()
	{
		given:
		def richard = addRichard()
		setCreationTimeOfMandatoryGoalsToNow(richard)
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
		setCreationTimeOfMandatoryGoalsToNow(richard)
		User bob = richardAndBob.bob
		setCreationTimeOfMandatoryGoalsToNow(bob)

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
			"Wed" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalSocialRichard, data: [goalAccomplished: false, minutesBeyondGoal: 1, spread: [60 : 1]]]],
			"Thu" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalSocialRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [46 : 1]]]],
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

		def buddyWeekOverviewLastWeek = responseBuddyWeekOverviews.responseData._embedded."yona:weekActivityOverviews"[1]
		assertNumberOfReportedDaysForGoalInWeekOverview(buddyWeekOverviewLastWeek, budgetGoalSocialBob, 3)
		assertDayInWeekOverviewForGoal(buddyWeekOverviewLastWeek, budgetGoalSocialBob, expectedValuesBobLastWeek, "Thu")
		assertDayInWeekOverviewForGoal(buddyWeekOverviewLastWeek, budgetGoalSocialBob, expectedValuesBobLastWeek, "Fri")
		assertDayInWeekOverviewForGoal(buddyWeekOverviewLastWeek, budgetGoalSocialBob, expectedValuesBobLastWeek, "Sat")

		assertWeekDetailForGoal(richard, buddyWeekOverviewLastWeek, budgetGoalSocialBob, expectedValuesBobLastWeek)

		assertNumberOfReportedDaysForGoalInWeekOverview(buddyWeekOverviewLastWeek, timeZoneGoalMultimediaBob, 2)
		assertDayInWeekOverviewForGoal(buddyWeekOverviewLastWeek, timeZoneGoalMultimediaBob, expectedValuesBobLastWeek, "Fri")
		assertDayInWeekOverviewForGoal(buddyWeekOverviewLastWeek, timeZoneGoalMultimediaBob, expectedValuesBobLastWeek, "Sat")

		assertDayDetail(richard, responseBuddyDayOverviews, budgetGoalSocialBob, expectedValuesBobLastWeek, 1, "Thu")
		assertDayDetail(richard, responseBuddyDayOverviews, budgetGoalSocialBob, expectedValuesBobLastWeek, 1, "Fri")
		assertDayDetail(richard, responseBuddyDayOverviews, budgetGoalSocialBob, expectedValuesBobLastWeek, 1, "Sat")

		assertDayDetail(richard, responseBuddyDayOverviews, timeZoneGoalMultimediaBob, expectedValuesBobLastWeek, 1, "Fri")
		assertDayDetail(richard, responseBuddyDayOverviews, timeZoneGoalMultimediaBob, expectedValuesBobLastWeek, 1, "Sat")

		// Assert self-links of day and week overviews
		def weekOverviewLink = weekOverviewLastWeek._links.self.href
		def weekOverviewResponse = appService.getResourceWithPassword(weekOverviewLink, richard.password)
		assertResponseStatusOk(weekOverviewResponse)
		assert weekOverviewResponse.data.date == weekOverviewLastWeek.date

		def dayOverviewOffset = YonaServer.relativeDateStringToDaysOffset(1, "Thu")
		def dayOverviewLink = responseDayOverviewsAll.responseData._embedded."yona:dayActivityOverviews"[dayOverviewOffset]._links.self.href
		def dayOverviewResponse = appService.getResourceWithPassword(dayOverviewLink, richard.password)
		assertResponseStatusOk(dayOverviewResponse)
		assert dayOverviewResponse.data.date == responseDayOverviewsAll.responseData._embedded."yona:dayActivityOverviews"[dayOverviewOffset].date

		def dayOverviewWithBuddiesOffset = YonaServer.relativeDateStringToDaysOffset(1, "Fri")
		def dayOverviewWithBuddiesLink = responseDayOverviewsWithBuddies.responseData._embedded."yona:dayActivityOverviews"[dayOverviewWithBuddiesOffset]._links.self.href
		def dayOverviewWithBuddiesResponse = appService.getResourceWithPassword(dayOverviewWithBuddiesLink, richard.password)
		assertResponseStatusOk(dayOverviewWithBuddiesResponse)
		assert dayOverviewWithBuddiesResponse.data.date == responseDayOverviewsWithBuddies.responseData._embedded."yona:dayActivityOverviews"[dayOverviewWithBuddiesOffset].date

		def buddyWeekOverviewLink = buddyWeekOverviewLastWeek._links.self.href
		def responseBuddyWeekOverview = appService.getResourceWithPassword(buddyWeekOverviewLink, richard.password)
		assertResponseStatusOk(responseBuddyWeekOverview)
		assert responseBuddyWeekOverview.data.date == buddyWeekOverviewLastWeek.date

		def buddyDayOverviewOffset = YonaServer.relativeDateStringToDaysOffset(1, "Sat")
		def buddyDayOverviewLink = responseBuddyDayOverviews.responseData._embedded."yona:dayActivityOverviews"[buddyDayOverviewOffset]._links.self.href
		def responseBuddyDayOverview = appService.getResourceWithPassword(buddyDayOverviewLink, richard.password)
		assertResponseStatusOk(responseBuddyDayOverview)
		assert responseBuddyDayOverview.data.date == responseBuddyDayOverviews.responseData._embedded."yona:dayActivityOverviews"[buddyDayOverviewOffset].date

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Activity before goal creation should be ignored'()
	{
		given:
		def richard = addRichard()
		setCreationTimeOfMandatoryGoalsToNow(richard)

		reportNetworkActivity(richard, ["YouTube"], "http://www.youtube.com", "W-2 Fri 09:00") // Should be ignored, as there was no goal yet
		TimeZoneGoal timeZoneGoalMultimediaBobBeforeUpdate = addTimeZoneGoal(richard, MULTIMEDIA_ACT_CAT_URL, ["20:00-22:00"], "W-1 Fri 14:00")
		reportNetworkActivity(richard, ["YouTube"], "http://www.youtube.com", "W-1 Fri 15:00")
		reportNetworkActivity(richard, ["YouTube"], "http://www.youtube.com", "W-1 Fri 21:00")

		richard = appService.reloadUser(richard)
		TimeZoneGoal timeZoneGoalMultimediaRichard = richard.findActiveGoal(MULTIMEDIA_ACT_CAT_URL)
		def expectedValuesRichardLastWeek = [
			"Fri" : [[goal:timeZoneGoalMultimediaRichard, data: [goalAccomplished: false, minutesBeyondGoal: 1, spread: [60 : 1, 84 : 1]]]],
			"Sat" : [[goal:timeZoneGoalMultimediaRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]]]

		def currentDayOfWeek = YonaServer.getCurrentDayOfWeek()
		def expectedTotalDays = 2 + currentDayOfWeek + 1
		def expectedTotalWeeks = 2

		when:
		def responseWeekOverviews = appService.getWeekActivityOverviews(richard, ["size": expectedTotalWeeks])
		def responseDayOverviewsAll = appService.getDayActivityOverviews(richard, ["size": 14])

		then:
		assertWeekOverviewBasics(responseWeekOverviews, [3, 1], expectedTotalWeeks, expectedTotalWeeks)
		assertWeekDateForCurrentWeek(responseWeekOverviews)

		def weekOverviewLastWeek = responseWeekOverviews.responseData._embedded."yona:weekActivityOverviews"[1]
		assertNumberOfReportedDaysForGoalInWeekOverview(weekOverviewLastWeek, timeZoneGoalMultimediaRichard, 2)
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, timeZoneGoalMultimediaRichard, expectedValuesRichardLastWeek, "Fri")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, timeZoneGoalMultimediaRichard, expectedValuesRichardLastWeek, "Sat")

		assertDayOverviewBasics(responseDayOverviewsAll, expectedTotalDays, expectedTotalDays, 14)
		assertDayOverviewForTimeZoneGoal(responseDayOverviewsAll, timeZoneGoalMultimediaRichard, expectedValuesRichardLastWeek, 1, "Fri")
		assertDayOverviewForTimeZoneGoal(responseDayOverviewsAll, timeZoneGoalMultimediaRichard, expectedValuesRichardLastWeek, 1, "Sat")

		cleanup:
		appService.deleteUser(richard)
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
		assertResponseStatusOk(initialResponseWeekOverviews)
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
		setCreationTimeOfMandatoryGoalsToNow(richard)
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
		assertResponseStatusOk(initialResponseWeekOverviews)
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
		setCreationTimeOfMandatoryGoalsToNow(richard)
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
		assertResponseStatusOk(response)
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
		assertResponseStatusOk(appService.getDayActivityOverviews(richard))
		assertResponseStatusOk(appService.getWeekActivityOverviews(richard))

		when:
		addBudgetGoal(richard, COMMUNICATION_ACT_CAT_URL, 60)
		then:
		assertResponseStatusOk(appService.getDayActivityOverviews(richard))
		assertResponseStatusOk(appService.getWeekActivityOverviews(richard))

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Retrieve inactive activity report current day/week before and after update of time zone goal'()
	{
		given:
		User richard = addRichard()
		Goal timeZoneGoalMultimedia = addTimeZoneGoal(richard, MULTIMEDIA_ACT_CAT_URL, ["11:00-12:00"])
		assertResponseStatusOk(appService.getDayActivityOverviews(richard))
		assertResponseStatusOk(appService.getWeekActivityOverviews(richard))

		when:
		updateTimeZoneGoal(richard, timeZoneGoalMultimedia, ["13:00-14:00"])

		then:
		assertResponseStatusOk(appService.getDayActivityOverviews(richard))
		assertResponseStatusOk(appService.getWeekActivityOverviews(richard))

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Retrieve inactive activity report current day/week before and after update of budget goal'()
	{
		given:
		User richard = addRichard()
		Goal budgetGoalMultimedia = addBudgetGoal(richard, MULTIMEDIA_ACT_CAT_URL, 60)
		assertResponseStatusOk(appService.getDayActivityOverviews(richard))
		assertResponseStatusOk(appService.getWeekActivityOverviews(richard))

		when:
		updateBudgetGoal(richard, budgetGoalMultimedia, 81)

		then:
		assertResponseStatusOk(appService.getDayActivityOverviews(richard))
		assertResponseStatusOk(appService.getWeekActivityOverviews(richard))

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Retrieve inactive activity report past week, after goal update in earlier week'()
	{
		given:
		User richard = addRichard()
		setCreationTimeOfMandatoryGoalsToNow(richard)
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
		setCreationTimeOfMandatoryGoalsToNow(richard)
		User bob = richardAndBob.bob
		setCreationTimeOfMandatoryGoalsToNow(bob)

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

	def 'Delete goal after update'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		setCreationTimeOfMandatoryGoalsToNow(richard)
		User bob = richardAndBob.bob
		setCreationTimeOfMandatoryGoalsToNow(bob)

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
		BudgetGoal budgetGoalNewsRichardAfterUpdate = updateBudgetGoal(richard, budgetGoalNewsRichardBeforeUpdate, 60, "W-1 Wed 20:51")
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

		assertResponseStatusOk(appService.removeGoal(richard, budgetGoalNewsRichardAfterUpdate))

		richard = appService.reloadUser(richard)
		timeZoneGoalSocialRichardBeforeUpdate = richard.goals.find{ it.activityCategoryUrl == SOCIAL_ACT_CAT_URL && it.historyItem }
		TimeZoneGoal timeZoneGoalSocialRichardAfterUpdate = richard.findActiveGoal(SOCIAL_ACT_CAT_URL)

		budgetGoalSocialBobBeforeUpdate = richard.buddies[0].goals.find{ it.activityCategoryUrl == SOCIAL_ACT_CAT_URL && it.historyItem }
		BudgetGoal budgetGoalSocialBobAfterUpdate = richard.buddies[0].goals.find{ it.activityCategoryUrl == SOCIAL_ACT_CAT_URL && !it.historyItem}
		timeZoneGoalMultimediaBobBeforeUpdate = richard.buddies[0].goals.find{ it.activityCategoryUrl == MULTIMEDIA_ACT_CAT_URL && it.historyItem }
		TimeZoneGoal timeZoneGoalMultimediaBobAfterUpdate = richard.buddies[0].goals.find{ it.activityCategoryUrl == MULTIMEDIA_ACT_CAT_URL && !it.historyItem}

		def expectedValuesRichardWeekBeforeLastWeek = []
		def expectedValuesBobWeekBeforeLastWeek = [
			"Thu" : [[goal:budgetGoalSocialBobBeforeUpdate, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [80 : 15, 81 : 15, 82 : 5]]]],
			"Fri" : [[goal:budgetGoalSocialBobBeforeUpdate, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalMultimediaBobBeforeUpdate, data: [goalAccomplished: false, minutesBeyondGoal: 1, spread: [60 : 1, 84 : 1]]]],
			"Sat" : [[goal:budgetGoalSocialBobBeforeUpdate, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalMultimediaBobBeforeUpdate, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]]]
		def expectedValuesWithBuddiesWeekBeforeLastWeek = [[ user : richard, expectedValues: expectedValuesRichardWeekBeforeLastWeek], [ user : richard.buddies[0].user, expectedValues: expectedValuesBobWeekBeforeLastWeek]]

		def expectedValuesRichardLastWeek = [
			"Thu" : [[goal:timeZoneGoalSocialRichardBeforeUpdate, data: [goalAccomplished: false, minutesBeyondGoal: 1, spread: [68 : 1]]]],
			"Fri" : [[goal:timeZoneGoalSocialRichardAfterUpdate, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Sat" : [[goal:timeZoneGoalSocialRichardAfterUpdate, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [ 82 : 2, 83 : 15, 84 : 1]]]]]
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
		def expectedTotalDaysRichard = 3 + currentDayOfWeek + 1
		def expectedTotalDaysBob = 3 + 7 + currentDayOfWeek + 1
		def expectedTotalWeeksRichard = 2
		def expectedTotalWeeksBob = 3

		when:
		def responseWeekOverviews = appService.getWeekActivityOverviews(richard, ["size": expectedTotalWeeksRichard])
		def responseDayOverviewsAll = appService.getDayActivityOverviews(richard, ["size": 21])
		def responseDayOverviewsWithBuddies = appService.getDayActivityOverviewsWithBuddies(richard, ["size": 21])
		def responseBuddyWeekOverviews = appService.getWeekActivityOverviews(richard, richard.buddies[0], ["size": expectedTotalWeeksBob])
		def responseBuddyDayOverviews = appService.getDayActivityOverviews(richard, richard.buddies[0], ["size": 21])

		then:
		assertWeekOverviewBasics(responseWeekOverviews, [2, 1], expectedTotalWeeksRichard, expectedTotalWeeksRichard)
		assertWeekDateForCurrentWeek(responseWeekOverviews)

		def weekOverviewLastWeek = responseWeekOverviews.responseData._embedded."yona:weekActivityOverviews"[1]
		assertNumberOfReportedDaysForGoalInWeekOverview(weekOverviewLastWeek, timeZoneGoalSocialRichardAfterUpdate, 3)
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, timeZoneGoalSocialRichardBeforeUpdate, expectedValuesRichardLastWeek, "Thu")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, timeZoneGoalSocialRichardAfterUpdate, expectedValuesRichardLastWeek, "Fri")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, timeZoneGoalSocialRichardAfterUpdate, expectedValuesRichardLastWeek, "Sat")

		assertDayOverviewBasics(responseDayOverviewsAll, expectedTotalDaysRichard, expectedTotalDaysRichard, 21)

		assertDayOverviewForTimeZoneGoal(responseDayOverviewsAll, timeZoneGoalSocialRichardBeforeUpdate, expectedValuesRichardLastWeek, 1, "Thu")
		assertDayOverviewForTimeZoneGoal(responseDayOverviewsAll, timeZoneGoalSocialRichardAfterUpdate, expectedValuesRichardLastWeek, 1, "Fri")
		assertDayOverviewForTimeZoneGoal(responseDayOverviewsAll, timeZoneGoalSocialRichardAfterUpdate, expectedValuesRichardLastWeek, 1, "Sat")

		assertDayDetail(richard, responseDayOverviewsAll, timeZoneGoalSocialRichardBeforeUpdate, expectedValuesRichardLastWeek, 1, "Thu")
		assertDayDetail(richard, responseDayOverviewsAll, timeZoneGoalSocialRichardAfterUpdate, expectedValuesRichardLastWeek, 1, "Fri")
		assertDayDetail(richard, responseDayOverviewsAll, timeZoneGoalSocialRichardAfterUpdate, expectedValuesRichardLastWeek, 1, "Sat")

		assertDayOverviewWithBuddiesBasics(responseDayOverviewsWithBuddies, expectedTotalDaysBob, expectedTotalDaysBob, 21)
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

	def 'Richard retrieves buddy activity info before Bob accepted Richard\'s buddy request'()
	{
		given:
		User richard = addRichard()
		User bob = addBob()
		bob.emailAddress = "bob@dunn.net"
		appService.sendBuddyConnectRequest(richard, bob)
		richard = appService.reloadUser(richard)

		when:
		def responseDayOverviewsWithBuddies = appService.getDayActivityOverviewsWithBuddies(richard, ["size": 14])

		then:
		richard.buddies[0].dailyActivityReportsUrl == null
		richard.buddies[0].weeklyActivityReportsUrl == null
		assertResponseStatusOk(responseDayOverviewsWithBuddies)
		responseDayOverviewsWithBuddies.responseData?._embedded == null // Richard doesn't know Bob's goals yet

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Bob retrieves buddy activity info before Richard processed Bob\'s acceptance'()
	{
		given:
		User richard = addRichard()
		User bob = addBob()
		bob.emailAddress = "bob@dunn.net"
		appService.sendBuddyConnectRequest(richard, bob)
		def connectRequestMessage = appService.fetchBuddyConnectRequestMessage(bob)
		def acceptUrl = connectRequestMessage.acceptUrl
		assertResponseStatusOk(appService.postMessageActionWithPassword(acceptUrl, ["message" : "Yes, great idea!"], bob.password))
		richard = appService.reloadUser(richard)
		bob = appService.reloadUser(bob)
		Goal budgetGoalGamblingRichard = bob.buddies[0].findActiveGoal(GAMBLING_ACT_CAT_URL)

		when:
		def responseDayOverviewsWithBuddies = appService.getDayActivityOverviewsWithBuddies(bob, ["size": 14])

		then:
		assertResponseStatusOk(responseDayOverviewsWithBuddies)
		responseDayOverviewsWithBuddies.responseData._embedded."yona:dayActivityOverviews"[0].dayActivities.find{ it._links."yona:activityCategory"?.href == GAMBLING_ACT_CAT_URL}.dayActivitiesForUsers.size() == 2
		def responseWeekOverviews = appService.getWeekActivityOverviews(bob, bob.buddies[0])
		assertResponseStatusOk(responseWeekOverviews)
		responseWeekOverviews.responseData._embedded."yona:weekActivityOverviews"[0].weekActivities.find{ it._links."yona:goal".href == budgetGoalGamblingRichard.url}
		def weekActivityForGoal = responseWeekOverviews.responseData._embedded."yona:weekActivityOverviews"[0].weekActivities.find{ it._links."yona:goal".href == budgetGoalGamblingRichard.url}
		def dayActivityInWeekForGoal = weekActivityForGoal.dayActivities[YonaServer.now.dayOfWeek.toString()]
		dayActivityInWeekForGoal.totalActivityDurationMinutes == 0

		def responseDayOverviews = appService.getDayActivityOverviews(bob, bob.buddies[0])

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Richard\'s can see his raw activities'()
	{
		given:
		def richard = addRichard()

		setGoalCreationTime(richard, NEWS_ACT_CAT_URL, "W-1 Mon 02:18")
		richard = appService.reloadUser(richard)

		def app = "NU.nl"
		def appActStartTime = "W-1 Wed 03:15"
		def appActEndTime = "W-1 Wed 03:35"
		reportAppActivity(richard, app, appActStartTime, appActEndTime)
		def netActStartTime = "W-1 Wed 15:00"
		reportNetworkActivity(richard, ["News"], "http://rd.nl", netActStartTime)

		def goalId = richard.findActiveGoal(NEWS_ACT_CAT_URL).getId()
		def lastWednesdayDate = YonaServer.toIsoDateString(YonaServer.relativeDateTimeStringToZonedDateTime("W-1 Wed 09:00"))
		def detailsUrl = YonaServer.stripQueryString(richard.url) + "/activity/days/$lastWednesdayDate/details/$goalId"
		assertResponseStatusOk(appService.getResourceWithPassword(detailsUrl, richard.password))

		when:
		def response = appService.getResourceWithPassword(detailsUrl + "/raw/", richard.password)

		then:
		assertResponseStatusOk(response)
		response.responseData._embedded."yona:activities".size == 2
		response.responseData._embedded."yona:activities"[0].startTime == YonaServer.toIsoDateTimeString(YonaServer.relativeDateTimeStringToZonedDateTime(appActStartTime))
		response.responseData._embedded."yona:activities"[0].endTime == YonaServer.toIsoDateTimeString(YonaServer.relativeDateTimeStringToZonedDateTime(appActEndTime))
		response.responseData._embedded."yona:activities"[0].app == app
		response.responseData._embedded."yona:activities"[1].startTime == YonaServer.toIsoDateTimeString(YonaServer.relativeDateTimeStringToZonedDateTime(netActStartTime))
		response.responseData._embedded."yona:activities"[1].endTime == YonaServer.toIsoDateTimeString(YonaServer.relativeDateTimeStringToZonedDateTime(netActStartTime).plusMinutes(1))
		response.responseData._embedded."yona:activities"[1].app == ""

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Try get raw activities with an invalid date'()
	{
		given:
		def richard = addRichard()

		setGoalCreationTime(richard, NEWS_ACT_CAT_URL, "W-1 Mon 02:18")
		richard = appService.reloadUser(richard)

		def goalId = richard.findActiveGoal(NEWS_ACT_CAT_URL).getId()
		def lastWednesdayDate = YonaServer.toIsoDateString(YonaServer.relativeDateTimeStringToZonedDateTime("W-1 Wed 09:00"))
		def invalidDate = lastWednesdayDate + "1234"
		def detailsUrl = YonaServer.stripQueryString(richard.url) + "/activity/days/$invalidDate/details/$goalId"

		when:
		def response = appService.getResourceWithPassword(detailsUrl + "/raw/", richard.password)

		then:
		assertResponseStatus(response, 400)
		assert response.responseData.code == "error.invalid.date"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Richard\'s goals are only included when a buddy has that goal too'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		setCreationTimeOfMandatoryGoalsToNow(richard)
		User bob = richardAndBob.bob
		setCreationTimeOfMandatoryGoalsToNow(bob)

		BudgetGoal budgetGoalNewsRichard = richard.findActiveGoal(NEWS_ACT_CAT_URL)
		BudgetGoal budgetGoalNewsBob = richard.buddies[0].goals.find{ it.activityCategoryUrl == NEWS_ACT_CAT_URL }

		setGoalCreationTime(richard, NEWS_ACT_CAT_URL, "W-2 Mon 02:18")
		setGoalCreationTime(bob, NEWS_ACT_CAT_URL, "W-2 Mon 02:18")

		TimeZoneGoal timeZoneGoalSocialRichard = addTimeZoneGoal(richard, SOCIAL_ACT_CAT_URL, ["11:00-12:00"], "W-2 Thu 13:55")
		addTimeZoneGoal(bob, MULTIMEDIA_ACT_CAT_URL, ["20:00-22:00"], "W-2 Thu 13:55")

		richard = appService.reloadUser(richard)
		bob= appService.reloadUser(bob)

		TimeZoneGoal timeZoneGoalMultimediaBob = richard.buddies[0].goals.find{ it.activityCategoryUrl == MULTIMEDIA_ACT_CAT_URL }
		assert timeZoneGoalMultimediaBob

		def expectedValuesRichardWeekBeforeLastWeek = [
			"Mon" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Tue" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Wed" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Thu" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalSocialRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Fri" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalSocialRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Sat" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalSocialRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]]]
		def expectedValuesBobWeekBeforeLastWeek = [
			"Mon" : [[goal:budgetGoalNewsBob, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Tue" : [[goal:budgetGoalNewsBob, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Wed" : [[goal:budgetGoalNewsBob, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Thu" : [[goal:budgetGoalNewsBob, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalMultimediaBob, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Fri" : [[goal:budgetGoalNewsBob, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalMultimediaBob, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Sat" : [[goal:budgetGoalNewsBob, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalMultimediaBob, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]]]
		def expectedValuesWithBuddiesWeekBeforeLastWeek = [[ user : richard, expectedValues: expectedValuesRichardWeekBeforeLastWeek], [ user : richard.buddies[0].user, expectedValues: expectedValuesBobWeekBeforeLastWeek]]

		def expectedValuesRichardLastWeek = [
			"Sun" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalSocialRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Mon" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalSocialRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Tue" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalSocialRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Wed" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalSocialRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Thu" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalSocialRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Fri" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalSocialRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Sat" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalSocialRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]]]
		def expectedValuesBobLastWeek = [
			"Sun" : [[goal:budgetGoalNewsBob, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalMultimediaBob, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Mon" : [[goal:budgetGoalNewsBob, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalMultimediaBob, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Tue" : [[goal:budgetGoalNewsBob, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalMultimediaBob, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Wed" : [[goal:budgetGoalNewsBob, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalMultimediaBob, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Thu" : [[goal:budgetGoalNewsBob, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalMultimediaBob, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Fri" : [[goal:budgetGoalNewsBob, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalMultimediaBob, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]],
			"Sat" : [[goal:budgetGoalNewsBob, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalMultimediaBob, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]]]]
		def expectedValuesWithBuddiesLastWeek = [[ user : richard, expectedValues: expectedValuesRichardLastWeek], [ user : richard.buddies[0].user, expectedValues: expectedValuesBobLastWeek]]

		def currentDayOfWeek = YonaServer.getCurrentDayOfWeek()
		def expectedTotalDays = 6 + 7 + currentDayOfWeek + 1
		def expectedTotalWeeks = 3

		when:
		def responseDayOverviewsWithBuddies = appService.getDayActivityOverviewsWithBuddies(richard, ["size": 21])

		then:

		assertDayOverviewWithBuddiesBasics(responseDayOverviewsWithBuddies, expectedTotalDays, expectedTotalDays, 21)
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, NEWS_ACT_CAT_URL, expectedValuesWithBuddiesWeekBeforeLastWeek, 2, "Mon")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, NEWS_ACT_CAT_URL, expectedValuesWithBuddiesWeekBeforeLastWeek, 2, "Tue")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, NEWS_ACT_CAT_URL, expectedValuesWithBuddiesWeekBeforeLastWeek, 2, "Wed")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, NEWS_ACT_CAT_URL, expectedValuesWithBuddiesWeekBeforeLastWeek, 2, "Thu")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, NEWS_ACT_CAT_URL, expectedValuesWithBuddiesWeekBeforeLastWeek, 2, "Fri")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, NEWS_ACT_CAT_URL, expectedValuesWithBuddiesWeekBeforeLastWeek, 2, "Sat")

		def thu2 = responseDayOverviewsWithBuddies.responseData._embedded."yona:dayActivityOverviews"[YonaServer.relativeDateStringToDaysOffset(2, "Thu")]
		thu2.dayActivities.find{it._links."yona:activityCategory".href == SOCIAL_ACT_CAT_URL} == null // Richard has social but none of his buddies has it, so it's not included
		thu2.dayActivities.find{it._links."yona:activityCategory".href == MULTIMEDIA_ACT_CAT_URL} != null // Buddy has multimedia, so it's included, though Richard doesn't have it
		thu2.dayActivities.find{it._links."yona:activityCategory".href == MULTIMEDIA_ACT_CAT_URL}.dayActivitiesForUsers.size() == 1 // Only Bob

		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, MULTIMEDIA_ACT_CAT_URL, expectedValuesWithBuddiesWeekBeforeLastWeek, 2, "Thu")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, MULTIMEDIA_ACT_CAT_URL, expectedValuesWithBuddiesWeekBeforeLastWeek, 2, "Fri")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, MULTIMEDIA_ACT_CAT_URL, expectedValuesWithBuddiesWeekBeforeLastWeek, 2, "Sat")

		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, NEWS_ACT_CAT_URL, expectedValuesWithBuddiesLastWeek, 1, "Sun")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, NEWS_ACT_CAT_URL, expectedValuesWithBuddiesLastWeek, 1, "Mon")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, NEWS_ACT_CAT_URL, expectedValuesWithBuddiesLastWeek, 1, "Tue")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, NEWS_ACT_CAT_URL, expectedValuesWithBuddiesLastWeek, 1, "Wed")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, NEWS_ACT_CAT_URL, expectedValuesWithBuddiesLastWeek, 1, "Thu")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, NEWS_ACT_CAT_URL, expectedValuesWithBuddiesLastWeek, 1, "Fri")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, NEWS_ACT_CAT_URL, expectedValuesWithBuddiesLastWeek, 1, "Sat")

		def mon1 = responseDayOverviewsWithBuddies.responseData._embedded."yona:dayActivityOverviews"[YonaServer.relativeDateStringToDaysOffset(1, "Mon")]
		mon1.dayActivities.find{it._links."yona:activityCategory".href == SOCIAL_ACT_CAT_URL} == null // Nobody has social, so it's not included
		mon1.dayActivities.find{it._links."yona:activityCategory".href == MULTIMEDIA_ACT_CAT_URL} != null // Buddy has multimedia, so it's included
		mon1.dayActivities.find{it._links."yona:activityCategory".href == MULTIMEDIA_ACT_CAT_URL}.dayActivitiesForUsers.size() == 1 // Only Bob

		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, MULTIMEDIA_ACT_CAT_URL, expectedValuesWithBuddiesLastWeek, 1, "Sun")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, MULTIMEDIA_ACT_CAT_URL, expectedValuesWithBuddiesLastWeek, 1, "Mon")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, MULTIMEDIA_ACT_CAT_URL, expectedValuesWithBuddiesLastWeek, 1, "Tue")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, MULTIMEDIA_ACT_CAT_URL, expectedValuesWithBuddiesLastWeek, 1, "Wed")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, MULTIMEDIA_ACT_CAT_URL, expectedValuesWithBuddiesLastWeek, 1, "Thu")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, MULTIMEDIA_ACT_CAT_URL, expectedValuesWithBuddiesLastWeek, 1, "Fri")
		assertDayOverviewWithBuddies(responseDayOverviewsWithBuddies, richard, MULTIMEDIA_ACT_CAT_URL, expectedValuesWithBuddiesLastWeek, 1, "Sat")

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
		assertResponseStatusOk(responseRemoveBuddy)

		when:
		def responseDayOverviewsWithBuddies = appService.getDayActivityOverviewsWithBuddies(richard, ["size": 14])

		then:
		assertResponseStatusOk(responseDayOverviewsWithBuddies)
		responseDayOverviewsWithBuddies.responseData?._embedded == null // Richard doesn't know Bob's goals anymore

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Richard works on two devices'()
	{
		given:
		def defaultDeviceName = "Richard's iPhone"
		User richardDefault = addRichard()
		setCreationTimeOfMandatoryGoalsToNow(richardDefault)
		setGoalCreationTime(richardDefault, NEWS_ACT_CAT_URL, "W-1 Mon 02:18")
		addTimeZoneGoal(richardDefault, SOCIAL_ACT_CAT_URL, ["01:00-12:00"], "W-1 Mon 02:18")
		richardDefault = appService.reloadUser(richardDefault)
		Goal budgetGoalNewsRichard = richardDefault.findActiveGoal(NEWS_ACT_CAT_URL)
		Goal timeZoneGoalSocialRichard = richardDefault.findActiveGoal(SOCIAL_ACT_CAT_URL)

		def iphoneDeviceName = "My second iPhone"
		User richardIphone = appService.addDevice(richardDefault, iphoneDeviceName, "IOS", Device.SOME_APP_VERSION)

		// Activities on default device
		reportAppActivity(richardDefault, "NU.nl", "W-1 Mon 03:15", "W-1 Mon 03:35")
		reportNetworkActivity(richardDefault, ["News"], "http://www.nu.nl", "W-1 Mon 03:20") // Same device, within app activity
		reportNetworkActivity(richardDefault, ["News"], "http://www.nu.nl", "W-1 Mon 03:40") // Same device, outside app activity
		reportNetworkActivity(richardDefault, ["social"], "http://www.facebook.com", "W-1 Mon 03:20") // Same device, within app activity
		reportNetworkActivity(richardDefault, ["social"], "http://www.facebook.com", "W-1 Mon 03:42") // Same device, outside app activity
		reportAppActivity(richardDefault, "NU.nl", "W-1 Mon 04:15", "W-1 Mon 04:35")
		reportAppActivity(richardDefault, "NU.nl", "W-1 Mon 05:15", "W-1 Mon 05:35")

		// Activities on second device
		reportAppActivity(richardIphone, "NU.nl", "W-1 Mon 04:10", "W-1 Mon 04:20") // Partial overlap
		reportAppActivity(richardIphone, "NU.nl", "W-1 Mon 05:20", "W-1 Mon 05:30") // Full overlap
		reportAppActivity(richardIphone, "NU.nl", "W-1 Mon 06:15", "W-1 Mon 06:35") // No overlap at all

		def expectedValuesRichardLastWeek = [
			"Mon" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: false, minutesBeyondGoal: 86, spread: [13 : 15, 14 : 6, 15 : 0, 16 : 5, 17 : 15, 18 : 5, 19 : 0, 20 : 0, 21 : 15, 22 : 5, 23 : 0, 24 : 0, 25 : 15, 26 : 5]]], [goal:timeZoneGoalSocialRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [13 : 1, 14 : 1]]]],
			"Tue" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalSocialRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [ : ]]]],
			"Wed" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalSocialRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [ : ]]]],
			"Thu" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalSocialRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [ : ]]]],
			"Fri" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalSocialRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [ : ]]]],
			"Sat" : [[goal:budgetGoalNewsRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: []]], [goal:timeZoneGoalSocialRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [ : ]]]]]

		def expectedRawValuesNewsMon = [["startTime": "W-1 Mon 03:15",
				"endTime": "W-1 Mon 03:35",
				"app": "NU.nl",
				"deviceName": defaultDeviceName
			], [	"startTime": "W-1 Mon 03:20",
				"endTime": "W-1 Mon 03:21",
				"app": "",
				"deviceName": defaultDeviceName
			], [	"startTime": "W-1 Mon 03:40",
				"endTime": "W-1 Mon 03:41",
				"app": "",
				"deviceName": defaultDeviceName
			], [	"startTime": "W-1 Mon 04:15",
				"endTime": "W-1 Mon 04:35",
				"app": "NU.nl",
				"deviceName": defaultDeviceName
			], [	"startTime": "W-1 Mon 05:15",
				"endTime": "W-1 Mon 05:35",
				"app": "NU.nl",
				"deviceName": defaultDeviceName
			], [	"startTime": "W-1 Mon 04:10",
				"endTime": "W-1 Mon 04:20",
				"app": "NU.nl",
				"deviceName": iphoneDeviceName
			], [	"startTime": "W-1 Mon 05:20",
				"endTime": "W-1 Mon 05:30",
				"app": "NU.nl",
				"deviceName": iphoneDeviceName
			], [	"startTime": "W-1 Mon 06:15",
				"endTime": "W-1 Mon 06:35",
				"app": "NU.nl",
				"deviceName": iphoneDeviceName
			]]

		def expectedRawValuesSocialMon = [[	"startTime": "W-1 Mon 03:20",
				"endTime": "W-1 Mon 03:21",
				"app": "",
				"deviceName": defaultDeviceName
			], [	"startTime": "W-1 Mon 03:42",
				"endTime": "W-1 Mon 03:43",
				"app": "",
				"deviceName": defaultDeviceName
			]]

		def currentDayOfWeek = YonaServer.getCurrentDayOfWeek()
		def expectedTotalDays = 6 + currentDayOfWeek + 1
		def expectedTotalWeeks = 2

		when:
		def responseWeekOverviews = appService.getWeekActivityOverviews(richardDefault)
		//get all days at once (max 2 weeks) to make assertion easy
		def responseDayOverviewsAll = appService.getDayActivityOverviews(richardDefault, ["size": 14])

		then:
		assertWeekOverviewBasics(responseWeekOverviews, [3, 2], expectedTotalWeeks)
		assertWeekDateForCurrentWeek(responseWeekOverviews)

		def weekOverviewLastWeek = responseWeekOverviews.responseData._embedded."yona:weekActivityOverviews"[1]

		assertNumberOfReportedDaysForGoalInWeekOverview(weekOverviewLastWeek, budgetGoalNewsRichard, 6)
		assertNumberOfReportedDaysForGoalInWeekOverview(weekOverviewLastWeek, timeZoneGoalSocialRichard, 6)

		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, budgetGoalNewsRichard, expectedValuesRichardLastWeek, "Mon")
		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, timeZoneGoalSocialRichard, expectedValuesRichardLastWeek, "Mon")

		assertWeekDetailForGoal(richardDefault, weekOverviewLastWeek, budgetGoalNewsRichard, expectedValuesRichardLastWeek)
		assertWeekDetailForGoal(richardDefault, weekOverviewLastWeek, timeZoneGoalSocialRichard, expectedValuesRichardLastWeek)

		assertDayOverviewBasics(responseDayOverviewsAll, expectedTotalDays, expectedTotalDays, 14)
		assertDayOverviewForBudgetGoal(responseDayOverviewsAll, budgetGoalNewsRichard, expectedValuesRichardLastWeek, 1, "Mon")
		assertDayOverviewForTimeZoneGoal(responseDayOverviewsAll, timeZoneGoalSocialRichard, expectedValuesRichardLastWeek, 1, "Mon")

		assertDayDetail(richardDefault, responseDayOverviewsAll, budgetGoalNewsRichard, expectedValuesRichardLastWeek, 1, "Mon")
		assertDayDetail(richardDefault, responseDayOverviewsAll, timeZoneGoalSocialRichard, expectedValuesRichardLastWeek, 1, "Mon")

		def responseRawDataNews = getRawActivityData(richardDefault, "W-1 Mon 00:00", budgetGoalNewsRichard)
		assertRawActivityData(responseRawDataNews.responseData._embedded."yona:activities", expectedRawValuesNewsMon)

		def responseRawDataSocial = getRawActivityData(richardDefault, "W-1 Mon 00:00", timeZoneGoalSocialRichard)
		assertRawActivityData(responseRawDataSocial.responseData._embedded."yona:activities", expectedRawValuesSocialMon)

		cleanup:
		appService.deleteUser(richardDefault)
	}

	def 'Richard\'s network activity is measured properly on different OSes and different budget goals'(def operatingSystem, def budget, def expectedValue)
	{
		given:
		User richard = addRichard(false, operatingSystem)
		setCreationTimeOfMandatoryGoalsToNow(richard)
		addBudgetGoal(richard, SOCIAL_ACT_CAT_URL, budget, "W-1 Mon 02:18")
		richard = appService.reloadUser(richard, CommonAssertions.&assertUserGetResponseDetailsWithPrivateDataIgnoreDefaultDevice)
		BudgetGoal budgetGoalSocialRichard = richard.findActiveGoal(SOCIAL_ACT_CAT_URL)

		// Activities on default device
		reportNetworkActivity(richard, ["social"], "http://www.facebook.com", "W-1 Mon 03:20")
		reportNetworkActivity(richard, ["social"], "http://www.facebook.com", "W-1 Mon 03:24")

		def expectedGoalAccomplished = budget > 0
		def expectedMinutesBeyondGoal = expectedGoalAccomplished || expectedValue.size() == 0 ? 0 :  expectedValue.entrySet().iterator().next().value
		def expectedValuesRichardLastWeek = [
			"Mon" : [[goal:budgetGoalSocialRichard, data: [goalAccomplished: expectedGoalAccomplished, minutesBeyondGoal: expectedMinutesBeyondGoal, spread: expectedValue]]],
			"Tue" : [[goal:budgetGoalSocialRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [ : ]]]],
			"Wed" : [[goal:budgetGoalSocialRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [ : ]]]],
			"Thu" : [[goal:budgetGoalSocialRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [ : ]]]],
			"Fri" : [[goal:budgetGoalSocialRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [ : ]]]],
			"Sat" : [[goal:budgetGoalSocialRichard, data: [goalAccomplished: true, minutesBeyondGoal: 0, spread: [ : ]]]]]

		def currentDayOfWeek = YonaServer.getCurrentDayOfWeek()
		def expectedTotalDays = 6 + currentDayOfWeek + 1
		def expectedTotalWeeks = 2

		when:
		def responseWeekOverviews = appService.getWeekActivityOverviews(richard)
		//get all days at once (max 2 weeks) to make assertion easy
		def responseDayOverviewsAll = appService.getDayActivityOverviews(richard, ["size": 14])

		then:
		assertWeekOverviewBasics(responseWeekOverviews, [3, 1], expectedTotalWeeks)
		assertWeekDateForCurrentWeek(responseWeekOverviews)

		def weekOverviewLastWeek = responseWeekOverviews.responseData._embedded."yona:weekActivityOverviews"[1]

		assertNumberOfReportedDaysForGoalInWeekOverview(weekOverviewLastWeek, budgetGoalSocialRichard, 6)

		assertDayInWeekOverviewForGoal(weekOverviewLastWeek, budgetGoalSocialRichard, expectedValuesRichardLastWeek, "Mon")

		assertWeekDetailForGoal(richard, weekOverviewLastWeek, budgetGoalSocialRichard, expectedValuesRichardLastWeek)

		assertDayOverviewBasics(responseDayOverviewsAll, expectedTotalDays, expectedTotalDays, 14)
		assertDayOverviewForBudgetGoal(responseDayOverviewsAll, budgetGoalSocialRichard, expectedValuesRichardLastWeek, 1, "Mon")

		assertDayDetail(richard, responseDayOverviewsAll, budgetGoalSocialRichard, expectedValuesRichardLastWeek, 1, "Mon")

		cleanup:
		appService.deleteUser(richard)

		where:
		operatingSystem | budget | expectedValue
		"ANDROID" | 0 | [ 13 : 4]
		"ANDROID" | 60 | [ : ] // We only track no-go goals on Android, as app activity monitoring provides much better measurements
		"IOS" | 0 | [13 : 4]
		"IOS" | 60 | [13 : 4] // App activity monitoring is not possible on iOS, so we also track budget goals
	}

	private def getRawActivityData(User user, relativeDate, goal) {
		def mondayDate = YonaServer.toIsoDateString(YonaServer.relativeDateTimeStringToZonedDateTime(relativeDate))
		def rawDataUrl = "${YonaServer.stripQueryString(user.url)}/activity/days/${mondayDate}/details/${goal.getId()}/raw/"
		def responseRawData = appService.getResourceWithPassword(rawDataUrl, user.password)
		assertResponseStatusOk(responseRawData)
		return responseRawData
	}

	private void assertRawActivityData(actualData, expectedDataRelativeDates)
	{
		def expectedData = expectedDataRelativeDates.collect { activity ->
			activity.startTime = YonaServer.toIsoDateTimeString(YonaServer.relativeDateTimeStringToZonedDateTime(activity.startTime))
			activity.endTime = YonaServer.toIsoDateTimeString(YonaServer.relativeDateTimeStringToZonedDateTime(activity.endTime))
			activity  // Return activity object so new list contains activity objects
		}

		actualData.sort(this.&compareRawActivities)
		expectedData.sort(this.&compareRawActivities)
		assert actualData == expectedData
	}

	private int compareRawActivities(x, y)
	{
		if(x.startTime == y.startTime){
			x.deviceName <=> y.deviceName
		}else{
			x.startTime <=> y.startTime
		}
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

	private void assertWeekDetailPrevNextLinks(User user, weekActivityForGoal, expectedPrevWeekForGoal, expectedNextWeekForGoal)
	{
		def weekDetails = appService.getWeekDetailsForWeekFromOverviewItem(user, weekActivityForGoal)
		assert weekDetails?.responseData?._links?."prev"?.href == expectedPrevWeekForGoal?._links?."yona:weekDetails"?.href
		assert weekDetails?.responseData?._links?."next"?.href == expectedNextWeekForGoal?._links?."yona:weekDetails"?.href
	}

	private void assertDayDetailPrevNextLinks(User user, dayActivityForGoal, expectedPrevDayForGoal, expectedNextDayForGoal)
	{
		def dayDetails = appService.getDayDetailsForDayFromOverviewItem(user, dayActivityForGoal)
		assert dayDetails?.responseData?._links?."prev"?.href == expectedPrevDayForGoal?._links?."yona:dayDetails"?.href
		assert dayDetails?.responseData?._links?."next"?.href == expectedNextDayForGoal?._links?."yona:dayDetails"?.href
	}
}
