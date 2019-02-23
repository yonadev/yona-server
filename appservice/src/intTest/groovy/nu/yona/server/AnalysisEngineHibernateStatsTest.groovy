/*******************************************************************************
 * Copyright (c) 2015, 2019 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import groovy.json.*
import nu.yona.server.test.User
import spock.lang.Shared

/**
 * This test guards the database access statistics of the analysis engine service. This service is very performance critical,
 * so it is important that the database access is very limited.
 * 
 * Changes in the Yona server implementation might lead to different statistics. If the new values are less than before,
 * updating the test is perfectly fine. If they are more than before, the first attempt should be to decrease it to the
 * original level. Only when it's strictly necessary to create/read/update/delete more entities, the test should be updated
 * with higher expected values.
 */
class AnalysisEngineHibernateStatsTest extends AbstractAppServiceIntegrationTest
{
	@Shared
	User richard

	@Shared
	def statistics = new LinkedHashMap()

	def setupSpec()
	{
		analysisService.setEnableStatistics(false) // Fail fast when server stats are disabled

		def richardAndBob = addRichardAndBobAsBuddies()
		richard = richardAndBob.richard
		addBudgetGoal(richard, SOCIAL_ACT_CAT_URL, 23*60)

		analysisService.setEnableStatistics(true)
	}

	def cleanupSpec()
	{
		analysisService.setEnableStatistics(false)
		appService.deleteUser(richard)
	}

	def 'Post of first network activity on nongoal causes limited reads and no writes' ()
	{
		given:
		analysisService.resetStatistics()
		analysisService.clearCaches()

		when:
		analysisService.postToAnalysisEngine(richard.requestingDevice, ["YouTube"], "http://www.youtube.com")

		then:
		def stats = analysisService.getStatistics()

		stats.entityDeleteCount == 0
		stats.entityFetchCount == 1
		stats.entityInsertCount == 0
		stats.entityLoadCount == 9
		stats.entityUpdateCount == 1 // TODO: Should be 0
		stats.transactionCount == 2
	}

	def 'Post of second network activity on nongoal causes no reads and no writes' ()
	{
		given:
		analysisService.clearCaches()
		analysisService.postToAnalysisEngine(richard.requestingDevice, ["YouTube"], "http://www.youtube.com")
		analysisService.resetStatistics()

		when:
		analysisService.postToAnalysisEngine(richard.requestingDevice, ["YouTube"], "http://www.youtube.com")

		then:
		def stats = analysisService.getStatistics()

		stats.entityDeleteCount == 0
		stats.entityFetchCount == 0
		stats.entityInsertCount == 0
		stats.entityLoadCount == 0
		stats.entityUpdateCount == 0
		stats.transactionCount == 1
	}

	def 'Post of first network activity on budget goal causes limited reads and writes' ()
	{
		given:
		analysisService.resetStatistics()
		analysisService.clearCaches()

		when:
		analysisService.postToAnalysisEngine(richard.requestingDevice, ["Facebook"], "http://www.facebook.com")

		then:
		def stats = analysisService.getStatistics()

		stats.entityDeleteCount == 0
		stats.entityFetchCount == 1
		stats.entityInsertCount == 3
		stats.entityLoadCount == 9
		stats.entityUpdateCount == 2
		stats.transactionCount == 2
	}

	def 'Post of second network activity on budget goal causes no reads and no writes' ()
	{
		given:
		analysisService.clearCaches()
		analysisService.postToAnalysisEngine(richard.requestingDevice, ["Facebook"], "http://www.facebook.com")
		analysisService.resetStatistics()

		when:
		analysisService.postToAnalysisEngine(richard.requestingDevice, ["Facebook"], "http://www.facebook.com")

		then:
		def stats = analysisService.getStatistics()

		stats.entityDeleteCount == 0
		stats.entityFetchCount == 0
		stats.entityInsertCount == 0
		stats.entityLoadCount == 0
		stats.entityUpdateCount == 0
		stats.transactionCount == 1
	}

	def 'Post of first network activity on no-go goal causes limited reads and writes' ()
	{
		given:
		analysisService.resetStatistics()
		analysisService.clearCaches()

		when:
		analysisService.postToAnalysisEngine(richard.requestingDevice, ["Gambling"], "http://www.poker.com")

		then:
		def stats = analysisService.getStatistics()

		stats.entityDeleteCount == 0
		stats.entityFetchCount == 1
		stats.entityInsertCount == 5
		stats.entityLoadCount == 19
		stats.entityUpdateCount == 4
		stats.transactionCount == 2
	}

	def 'Post of second network activity on no-go goal causes no reads and no writes' ()
	{
		given:
		analysisService.clearCaches()
		analysisService.postToAnalysisEngine(richard.requestingDevice, ["Gambling"], "http://www.poker.com")
		analysisService.resetStatistics()

		when:
		analysisService.postToAnalysisEngine(richard.requestingDevice, ["Gambling"], "http://www.poker.com")

		then:
		def stats = analysisService.getStatistics()

		stats.entityDeleteCount == 0
		stats.entityFetchCount == 0
		stats.entityInsertCount == 0
		stats.entityLoadCount == 0
		stats.entityUpdateCount == 0
		stats.transactionCount == 1
	}
}