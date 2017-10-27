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
import spock.lang.Shared

@IgnoreIf(
{
	!Boolean.valueOf(properties['yona.enableHibernateStatsAllowed'])
})
class HibernateStatsTest extends AbstractAppServiceIntegrationTest
{
	@Shared
	User richard

	@Shared
	def buddyUsers

	@Shared
	def statistics = new LinkedHashMap()

	def setupSpec()
	{
		appService.setEnableStatistics(false) // Fail fast when server stats are disabled

		int numBuddies = 3
		richard = addRichard()
		buddyUsers = createBuddies(richard, numBuddies)
		generateActivities(richard)
		buddyUsers.each { generateActivities(it) }
		richard = appService.reloadUser(richard)
		generateCommentThreadOnYesterdaysNewsActivity(richard, buddyUsers)
		assert batchService.triggerActivityAggregationBatchJob().status == 200
		assert richard.getBuddies().size() == numBuddies
		println "Test user URL: $richard.url, password: $richard.password"

		appService.setEnableStatistics(true)
	}

	def cleanupSpec()
	{
		appService.setEnableStatistics(false)
		YonaServer.storeStatistics(statistics, "HibernateStatsTest")
		buddyUsers.each{ appService.deleteUser(it) }
		appService.deleteUser(richard)
	}

	def 'Get user without private data - first request' ()
	{
		given:
		appService.resetStatistics()
		appService.clearCaches()

		when:
		appService.getUser(appService.&assertUserGetResponseDetailsWithoutPrivateData, richard.url, false, null)

		then:
		captureStatistics("GetUserWithoutPrivateDataFirst")
	}

	def 'Get user without private data - second request' ()
	{
		given:
		appService.clearCaches()
		appService.getUser(appService.&assertUserGetResponseDetailsWithoutPrivateData, richard.url, false, null)
		appService.resetStatistics()

		when:
		appService.getUser(appService.&assertUserGetResponseDetailsWithoutPrivateData, richard.url, false, null)

		then:
		captureStatistics("GetUserWithoutPrivateDataSecond")
	}

	def 'Get user with private data - first request' ()
	{
		given:
		appService.clearCaches()
		appService.resetStatistics()

		when:
		appService.reloadUser(richard)

		then:
		captureStatistics("GetUserWithPrivateDataFirst")
	}

	def 'Get user with private data - second request' ()
	{
		given:
		appService.clearCaches()
		appService.reloadUser(richard)
		appService.resetStatistics()

		when:
		appService.reloadUser(richard)

		then:
		captureStatistics("GetUserWithPrivateDataSecond")
	}

	def 'Get messages - first request' ()
	{
		given:
		appService.clearCaches()
		appService.reloadUser(richard)
		appService.resetStatistics()

		when:
		def response = appService.getMessages(richard)

		then:
		response.status == 200
		captureStatistics("GetMessagesFirst")
	}

	def 'Get messages - second request' ()
	{
		given:
		appService.clearCaches()
		appService.reloadUser(richard)
		assert appService.getMessages(richard).status == 200
		appService.resetStatistics()

		when:
		def response = appService.getMessages(richard)

		then:
		response.status == 200
		captureStatistics("GetMessagesSecond")
	}

	def 'Get day activity overviews - first request' ()
	{
		given:
		appService.clearCaches()
		appService.reloadUser(richard)
		appService.resetStatistics()

		when:
		def response = appService.getDayActivityOverviews(richard)

		then:
		response.status == 200
		captureStatistics("GetDayActivityOverviewsFirst")
	}

	def 'Get day activity overviews - second request' ()
	{
		given:
		appService.clearCaches()
		appService.reloadUser(richard)
		assert appService.getDayActivityOverviews(richard).status == 200
		appService.resetStatistics()

		when:
		def response = appService.getDayActivityOverviews(richard)

		then:
		response.status == 200
		captureStatistics("GetDayActivityOverviewsSecond")
	}

	def 'Get week activity overviews - first request' ()
	{
		given:
		appService.clearCaches()
		appService.reloadUser(richard)
		appService.resetStatistics()

		when:
		def response = appService.getWeekActivityOverviews(richard)

		then:
		response.status == 200
		captureStatistics("GetWeekActivityOverviewsFirst")
	}

	def 'Get week activity overviews - second request' ()
	{
		given:
		appService.clearCaches()
		appService.reloadUser(richard)
		assert appService.getWeekActivityOverviews(richard).status == 200
		appService.resetStatistics()

		when:
		def response = appService.getWeekActivityOverviews(richard)

		then:
		response.status == 200
		captureStatistics("GetWeekActivityOverviewsSecond")
	}

	def 'Get day activity overviews with buddies - first request' ()
	{
		given:
		appService.clearCaches()
		appService.reloadUser(richard)
		appService.resetStatistics()

		when:
		def response = appService.getDayActivityOverviewsWithBuddies(richard)

		then:
		response.status == 200
		captureStatistics("GetDayActivityOverviewsWithBuddiesFirst")
	}

	def 'Get day activity overviews with buddies - second request' ()
	{
		given:
		appService.clearCaches()
		appService.reloadUser(richard)
		assert appService.getDayActivityOverviewsWithBuddies(richard).status == 200
		appService.resetStatistics()

		when:
		def response = appService.getDayActivityOverviewsWithBuddies(richard)

		then:
		response.status == 200
		captureStatistics("GetDayActivityOverviewsWithBuddiesSecond")
	}

	def 'Get day activity details last Friday - first request' ()
	{
		given:
		appService.clearCaches()
		appService.reloadUser(richard)
		def goalId = richard.findActiveGoal(GAMBLING_ACT_CAT_URL).getId()
		def lastFridayDate = YonaServer.toIsoDateString(YonaServer.relativeDateTimeStringToZonedDateTime("W-1 Fri 09:00"))
		appService.resetStatistics()

		when:
		def response = appService.getResourceWithPassword(richard.url + "/activity/days/$lastFridayDate/details/$goalId", richard.password)

		then:
		response.status == 200
		captureStatistics("GetDayActivityDetailsLastFridayFirst")
	}

	def 'Get day activity details last Friday - second request' ()
	{
		given:
		appService.clearCaches()
		appService.reloadUser(richard)
		def goalId = richard.findActiveGoal(GAMBLING_ACT_CAT_URL).getId()
		def lastFridayDate = YonaServer.toIsoDateString(YonaServer.relativeDateTimeStringToZonedDateTime("W-1 Fri 09:00"))
		assert appService.getResourceWithPassword(richard.url + "/activity/days/$lastFridayDate/details/$goalId", richard.password).status == 200
		appService.resetStatistics()

		when:
		def response = appService.getResourceWithPassword(richard.url + "/activity/days/$lastFridayDate/details/$goalId", richard.password)

		then:
		response.status == 200
		captureStatistics("GetDayActivityDetailsLastFridaySecond")
	}

	def 'Get week activity details last week - first request' ()
	{
		given:
		appService.clearCaches()
		appService.reloadUser(richard)
		def goalId = richard.findActiveGoal(GAMBLING_ACT_CAT_URL).getId()
		def lastWeek = YonaServer.toIsoWeekDateString(YonaServer.relativeDateTimeStringToZonedDateTime("W-1 Fri 09:00"))
		appService.resetStatistics()

		when:
		def response = appService.getResourceWithPassword(richard.url + "/activity/weeks/$lastWeek/details/$goalId", richard.password)

		then:
		response.status == 200
		captureStatistics("GetDayActivityDetailsLastFridayFirst")
	}

	def 'Get week activity details last week - second request' ()
	{
		given:
		appService.clearCaches()
		appService.reloadUser(richard)
		def goalId = richard.findActiveGoal(GAMBLING_ACT_CAT_URL).getId()
		def lastWeek = YonaServer.toIsoWeekDateString(YonaServer.relativeDateTimeStringToZonedDateTime("W-1 Fri 09:00"))
		assert appService.getResourceWithPassword(richard.url + "/activity/weeks/$lastWeek/details/$goalId", richard.password).status == 200
		appService.resetStatistics()

		when:
		def response = appService.getResourceWithPassword(richard.url + "/activity/weeks/$lastWeek/details/$goalId", richard.password)

		then:
		response.status == 200
		captureStatistics("GetDayActivityDetailsLastFridaySecond")
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

	void captureStatistics(def tag)
	{
		statistics[tag] = appService.getStatistics()
	}
}