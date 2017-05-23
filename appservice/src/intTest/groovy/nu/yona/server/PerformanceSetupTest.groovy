/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import java.time.LocalDate
import java.time.format.TextStyle

import groovy.json.*
import nu.yona.server.test.BudgetGoal
import nu.yona.server.test.User
import spock.lang.IgnoreIf

class PerformanceSetupTest extends AbstractAppServiceIntegrationTest
{
	@IgnoreIf({ !Boolean.valueOf(properties['yona.test.gen.data']) })
	def 'Setup query optimization benchmark scenario'()
	{
		given:
		int numBuddies = 3
		User richard = addRichard()

		when:
		def buddyUsers = createBuddies(richard, numBuddies)
		generateActivities(richard)
		buddyUsers.each { generateActivities(it) }
		richard = appService.reloadUser(richard)
		generateCommentThreadOnYesterdaysNewsActivity(richard, buddyUsers)
		assert batchService.triggerActivityAggregationBatchJob().status == 200


		then:
		richard.getBuddies().size() == numBuddies
		println "Test user URL: $richard.url, password: $richard.password"
	}

	List<User> createBuddies(User user, int numBuddies)
	{
		def buddyUsers = []
		(1..numBuddies).each
		{
			User buddyUser = createBuddyUser(it)
			appService.makeBuddies(user, buddyUser)
			buddyUsers.add(appService.reloadUser(buddyUser))
		}
		return buddyUsers
	}

	User createBuddyUser(int index)
	{
		User buddyUser = appService.addUser(appService.&assertUserCreationResponseDetails, "Bob" + index, "Dunn" + index, "BD" + index,
				"+$timestamp")
		buddyUser = appService.confirmMobileNumber(appService.&assertResponseStatusSuccess, buddyUser)
		setGoals(buddyUser)
		buddyUser.emailAddress = "bob${index}@dunn.com"
		return buddyUser
	}

	void setGoals(User user)
	{
		setGoalCreationTime(user, GAMBLING_ACT_CAT_URL, "W-2 Sun 01:00")
		addBudgetGoals(user,  [NEWS_ACT_CAT_URL, MULTIMEDIA_ACT_CAT_URL])
		addTimeZoneGoals(user,  [ADULT_CONTENT_ACT_CAT_URL])
		createGoalHistoryItems(user, NEWS_ACT_CAT_URL)
		createGoalHistoryItems(user, MULTIMEDIA_ACT_CAT_URL)
	}
	void addBudgetGoals(User user, def goalUrls)
	{
		goalUrls.each {addBudgetGoal(user, it, 0, "W-2 Sun 01:00")}
	}

	void addTimeZoneGoals(User user, def goalUrls)
	{
		goalUrls.each {addTimeZoneGoal(user, it, ["11:00-12:00"], "W-2 Sun 01:00")}
	}

	void createGoalHistoryItems(User user, def goalUrl)
	{
		user = appService.reloadUser(user)
		BudgetGoal goal = user.findActiveGoal(goalUrl)
		updateBudgetGoal(user, goal, 10, "W-2 Tue 01:00")
		updateBudgetGoal(user, goal, 20, "W-2 Wed 01:00")
		updateBudgetGoal(user, goal, 30, "W-2 Thu 01:00")
	}

	void generateActivities(User user)
	{
		def apps = ["Bad app", "com.whatsapp", "Poker App", "nl.uitzendinggemist", "nl.sanomamedia.android.nu", "com.facebook.katana"]
		apps.each { generateAppActivityForEveryDay(user, it) }
	}

	void generateAppActivityForEveryDay(User user, def app)
	{
		(-2..-1).each {generateAppActivityForDaysOfWeek(user, app, "W$it", 7) }
		generateAppActivityForDaysOfWeek(user, app, "W0", YonaServer.getCurrentDayOfWeek() + 1)
	}

	void generateAppActivityForDaysOfWeek(User user, def app, def week, def numDays)
	{
		def days = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"]
		days[0..numDays-1].each { generateAppActivityForDay(user, app, "$week $it") }
	}

	void generateAppActivityForDay(User user, def app, def day)
	{
		def hoursBegin = ["00:05", "00:30", "00:55", "01:20"]
		def hoursEnd   = ["00:10", "00:35", "01:00", "01:25"]
		hoursBegin.eachWithIndex
		{ begin, idx ->
			reportAppActivity(user, app, "$day $begin", "$day " + hoursEnd[idx])
		}
	}
	void generateCommentThreadOnYesterdaysNewsActivity(User user, def buddyUsers)
	{
		User buddyUser = buddyUsers.find { it.nickname == user.buddies[0].nickname }
		def yesterdayShortDay = LocalDate.now().getDayOfWeek().minus(1).getDisplayName(TextStyle.SHORT, Locale.US)
		ActivityCommentTest.assertCommentingWorks(appService, user, buddyUser, NEWS_ACT_CAT_URL, false, {u -> appService.getDayActivityOverviews(u, ["size": 14])},
		{u -> appService.getDayActivityOverviews(u, u.buddies[0], ["size": 14])},
		{responseOverviews, u, goal -> appService.getDayDetailsFromOverview(responseOverviews, u, goal, 0, yesterdayShortDay)},
		{ u, message -> assertMarkReadUnread(u, message)})
	}
}