/*******************************************************************************
 * Copyright (c) 2015, 2019 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import static nu.yona.server.test.CommonAssertions.assertResponseStatusOk

import java.time.LocalDate
import java.time.format.TextStyle

import nu.yona.server.test.BudgetGoal
import nu.yona.server.test.CommonAssertions
import nu.yona.server.test.Device
import nu.yona.server.test.Goal
import nu.yona.server.test.User
import spock.lang.IgnoreIf
import spock.lang.Shared

@IgnoreIf({
	!Boolean.valueOf(properties['yona.enableHibernateStatsAllowed'] as String)
})
class HibernateStatsTest extends AbstractAppServiceIntegrationTest
{

	static final String SCENARIO_RELATIVE_START_TIME = "W-2 Sun 01:00"
	@Shared
	User richard

	@Shared
	List<User> buddyUsers

	@Shared
	def statistics = new LinkedHashMap()

	def setupSpec()
	{
		appService.setEnableStatistics(false) // Fail fast when server stats are disabled

		int numBuddies = 3
		richard = addRichard()
		setCreationTime(richard, SCENARIO_RELATIVE_START_TIME)
		buddyUsers = createBuddies(richard, numBuddies)
		generateActivities(richard)
		buddyUsers.each { generateActivities(it) }
		richard = appService.reloadUser(richard)
		generateCommentThreadOnYesterdaysNewsActivity(richard, buddyUsers)
		assertResponseStatusOk(batchService.triggerActivityAggregationBatchJob())
		assert richard.getBuddies().size() == numBuddies
		println "Test user URL: $richard.url, password: $richard.password"

		appService.setEnableStatistics(true)
	}

	def cleanupSpec()
	{
		appService.setEnableStatistics(false)
		YonaServer.storeStatistics(statistics, "HibernateStatsTest")
		buddyUsers.each { appService.deleteUser(it) }
		appService.deleteUser(richard)
	}

	def 'Get user - first request'()
	{
		given:
		appService.clearCaches()
		appService.resetStatistics()

		when:
		appService.reloadUser(richard)

		then:
		captureStatistics("GetUserWithPrivateDataFirst")
	}

	def 'Get user - second request'()
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

	def 'Get messages - first request'()
	{
		given:
		appService.clearCaches()
		appService.reloadUser(richard)
		appService.resetStatistics()

		when:
		def response = appService.getMessages(richard)

		then:
		assertResponseStatusOk(response)
		captureStatistics("GetMessagesFirst")
	}

	def 'Get messages - second request'()
	{
		given:
		appService.clearCaches()
		appService.reloadUser(richard)
		assertResponseStatusOk(appService.getMessages(richard))
		appService.resetStatistics()

		when:
		def response = appService.getMessages(richard)

		then:
		assertResponseStatusOk(response)
		captureStatistics("GetMessagesSecond")
	}

	def 'Get day activity overviews - first request'()
	{
		given:
		appService.clearCaches()
		appService.reloadUser(richard)
		appService.resetStatistics()

		when:
		def response = appService.getDayActivityOverviews(richard)

		then:
		assertResponseStatusOk(response)
		captureStatistics("GetDayActivityOverviewsFirst")
	}

	def 'Get day activity overviews - second request'()
	{
		given:
		appService.clearCaches()
		appService.reloadUser(richard)
		assertResponseStatusOk(appService.getDayActivityOverviews(richard))
		appService.resetStatistics()

		when:
		def response = appService.getDayActivityOverviews(richard)

		then:
		assertResponseStatusOk(response)
		captureStatistics("GetDayActivityOverviewsSecond")
	}

	def 'Get week activity overviews - first request'()
	{
		given:
		appService.clearCaches()
		appService.reloadUser(richard)
		appService.resetStatistics()

		when:
		def response = appService.getWeekActivityOverviews(richard)

		then:
		assertResponseStatusOk(response)
		captureStatistics("GetWeekActivityOverviewsFirst")
	}

	def 'Get week activity overviews - second request'()
	{
		given:
		appService.clearCaches()
		appService.reloadUser(richard)
		assertResponseStatusOk(appService.getWeekActivityOverviews(richard))
		appService.resetStatistics()

		when:
		def response = appService.getWeekActivityOverviews(richard)

		then:
		assertResponseStatusOk(response)
		captureStatistics("GetWeekActivityOverviewsSecond")
	}

	def 'Get day activity overviews with buddies - first request'()
	{
		given:
		appService.clearCaches()
		appService.reloadUser(richard)
		appService.resetStatistics()

		when:
		def response = appService.getDayActivityOverviewsWithBuddies(richard)

		then:
		assertResponseStatusOk(response)
		captureStatistics("GetDayActivityOverviewsWithBuddiesFirst")
	}

	def 'Get day activity overviews with buddies - second request'()
	{
		given:
		appService.clearCaches()
		appService.reloadUser(richard)
		assertResponseStatusOk(appService.getDayActivityOverviewsWithBuddies(richard))
		appService.resetStatistics()

		when:
		def response = appService.getDayActivityOverviewsWithBuddies(richard)

		then:
		assertResponseStatusOk(response)
		captureStatistics("GetDayActivityOverviewsWithBuddiesSecond")
	}

	def 'Get day activity details last Friday - first request'()
	{
		given:
		appService.clearCaches()
		appService.reloadUser(richard)
		def goalId = richard.findActiveGoal(GAMBLING_ACT_CAT_URL).getId()
		def lastFridayDate = YonaServer.toIsoDateString(YonaServer.relativeDateTimeStringToZonedDateTime("W-1 Fri 09:00"))
		appService.resetStatistics()

		when:
		def response = appService.getResourceWithPassword(YonaServer.stripQueryString(richard.url) + "/activity/days/$lastFridayDate/details/$goalId", richard.password)

		then:
		assertResponseStatusOk(response)
		captureStatistics("GetDayActivityDetailsLastFridayFirst")
	}

	def 'Get day activity details last Friday - second request'()
	{
		given:
		appService.clearCaches()
		appService.reloadUser(richard)
		def goalId = richard.findActiveGoal(GAMBLING_ACT_CAT_URL).getId()
		def lastFridayDate = YonaServer.toIsoDateString(YonaServer.relativeDateTimeStringToZonedDateTime("W-1 Fri 09:00"))
		assertResponseStatusOk(appService.getResourceWithPassword(YonaServer.stripQueryString(richard.url) + "/activity/days/$lastFridayDate/details/$goalId", richard.password))
		appService.resetStatistics()

		when:
		def response = appService.getResourceWithPassword(YonaServer.stripQueryString(richard.url) + "/activity/days/$lastFridayDate/details/$goalId", richard.password)

		then:
		assertResponseStatusOk(response)
		captureStatistics("GetDayActivityDetailsLastFridaySecond")
	}

	def 'Get week activity details last week - first request'()
	{
		given:
		appService.clearCaches()
		appService.reloadUser(richard)
		def goalId = richard.findActiveGoal(GAMBLING_ACT_CAT_URL).getId()
		def lastWeek = YonaServer.toIsoWeekDateString(YonaServer.relativeDateTimeStringToZonedDateTime("W-1 Fri 09:00"))
		appService.resetStatistics()

		when:
		def response = appService.getResourceWithPassword(YonaServer.stripQueryString(richard.url) + "/activity/weeks/$lastWeek/details/$goalId", richard.password)

		then:
		assertResponseStatusOk(response)
		captureStatistics("GetDayActivityDetailsLastFridayFirst")
	}

	def 'Get week activity details last week - second request'()
	{
		given:
		appService.clearCaches()
		appService.reloadUser(richard)
		def goalId = richard.findActiveGoal(GAMBLING_ACT_CAT_URL).getId()
		def lastWeek = YonaServer.toIsoWeekDateString(YonaServer.relativeDateTimeStringToZonedDateTime("W-1 Fri 09:00"))
		assertResponseStatusOk(appService.getResourceWithPassword(YonaServer.stripQueryString(richard.url) + "/activity/weeks/$lastWeek/details/$goalId", richard.password))
		appService.resetStatistics()

		when:
		def response = appService.getResourceWithPassword(YonaServer.stripQueryString(richard.url) + "/activity/weeks/$lastWeek/details/$goalId", richard.password)

		then:
		assertResponseStatusOk(response)
		captureStatistics("GetDayActivityDetailsLastFridaySecond")
	}

	List<User> createBuddies(User user, int numBuddies)
	{
		def buddyUsers = []
		(1..numBuddies).each {
			User buddyUser = createBuddyUser(it)
			appService.makeBuddies(user, buddyUser)
			buddyUser = appService.reloadUser(buddyUser)
			updateLastStatusChangeTime(buddyUser, buddyUser.buddies[0], SCENARIO_RELATIVE_START_TIME)
			buddyUsers.add(buddyUser)
		}
		appService.reloadUser(user).buddies.forEach { updateLastStatusChangeTime(user, it, SCENARIO_RELATIVE_START_TIME) }
		return buddyUsers
	}

	User createBuddyUser(int index)
	{
		def userName = "Bob" + index
		User buddyUser = appService.addUser(CommonAssertions.&assertUserCreationResponseDetails, userName, "Dunn" + index, "BD" + index,
				makeMobileNumber(timestamp), "$userName's S8", "ANDROID", Device.SOME_APP_VERSION, Device.SUPPORTED_APP_VERSION_CODE)
		buddyUser = appService.confirmMobileNumber(CommonAssertions.&assertResponseStatusSuccess, buddyUser)
		setCreationTime(buddyUser, SCENARIO_RELATIVE_START_TIME)
		setGoals(buddyUser)
		buddyUser.emailAddress = "bob${index}@dunn.com"
		return buddyUser
	}

	void setGoals(User user)
	{
		setGoalCreationTime(user, GAMBLING_ACT_CAT_URL, SCENARIO_RELATIVE_START_TIME)
		addBudgetGoals(user, [NEWS_ACT_CAT_URL, MULTIMEDIA_ACT_CAT_URL])
		addTimeZoneGoals(user, [ADULT_CONTENT_ACT_CAT_URL])
		createGoalHistoryItems(user, NEWS_ACT_CAT_URL)
		createGoalHistoryItems(user, MULTIMEDIA_ACT_CAT_URL)
	}

	void addBudgetGoals(User user, def goalUrls)
	{
		goalUrls.each { addBudgetGoal(user, it, 0, SCENARIO_RELATIVE_START_TIME) }
	}

	void addTimeZoneGoals(User user, def goalUrls)
	{
		goalUrls.each { addTimeZoneGoal(user, it, ["11:00-12:00"], SCENARIO_RELATIVE_START_TIME) }
	}

	void createGoalHistoryItems(User user, def goalUrl)
	{
		user = appService.reloadUser(user)
		BudgetGoal goal = user.findActiveGoal(goalUrl) as BudgetGoal
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
		(-2..-1).each { generateAppActivityForDaysOfWeek(user, app, "W$it", 7) }
		generateAppActivityForDaysOfWeek(user, app, "W0", YonaServer.getCurrentDayOfWeek() + 1)
	}

	void generateAppActivityForDaysOfWeek(User user, def app, def week, def numDays)
	{
		def days = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"]
		days[0..numDays - 1].each { generateAppActivityForDay(user, app, "$week $it") }
	}

	void generateAppActivityForDay(User user, def app, def day)
	{
		def hoursBegin = ["00:05", "00:30", "00:55", "01:20"]
		def hoursEnd = ["00:10", "00:35", "01:00", "01:25"]
		hoursBegin.eachWithIndex { begin, idx -> reportAppActivity(user, user.requestingDevice, app, "$day $begin", "$day " + hoursEnd[idx])
		}
	}

	void generateCommentThreadOnYesterdaysNewsActivity(User user, def buddyUsers)
	{
		User buddyUser = buddyUsers.find { it.nickname == user.buddies[0].user.nickname }
		def yesterdayShortDay = LocalDate.now().getDayOfWeek().minus(1).getDisplayName(TextStyle.SHORT, Locale.US)
		ActivityCommentTest.assertCommentingWorks(appService, user, buddyUser, NEWS_ACT_CAT_URL, false, { User u -> appService.getDayActivityOverviews(u, ["size": 14]) },
				{ User u -> appService.getDayActivityOverviews(u, u.buddies[0], ["size": 14]) },
				{ responseOverviews, User u, Goal goal -> appService.getDayDetailsFromOverview(responseOverviews, u, goal, 0, yesterdayShortDay) },
				{ User u, message -> assertMarkReadUnread(u, message) })
	}

	void captureStatistics(def tag)
	{
		statistics[tag] = appService.getStatistics()
	}
}
