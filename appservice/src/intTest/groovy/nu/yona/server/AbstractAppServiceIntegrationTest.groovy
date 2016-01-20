/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import groovy.json.*

import java.text.SimpleDateFormat

import nu.yona.server.test.AnalysisService
import nu.yona.server.test.AppService
import nu.yona.server.test.BudgetGoal
import spock.lang.Shared
import spock.lang.Specification

abstract class AbstractAppServiceIntegrationTest extends Specification
{
	@Shared
	def AnalysisService analysisService = new AnalysisService()

	@Shared
	def AppService appService = new AppService()

	@Shared
	private String baseTimestamp = createBaseTimestamp()

	@Shared
	private int sequenceNumber = 0

	def addRichard()
	{
		def richard = appService.addUser(appService.&assertUserCreationResponseDetails, "R i c h a r d", "Richard", "Quinn", "RQ",
				"+$timestamp", ["Nexus 6"], [
					BudgetGoal.createNoGoInstance("news"),
					BudgetGoal.createNoGoInstance("gambling")
				])
		appService.confirmMobileNumber(appService.&assertResponseStatusSuccess, richard)
		return richard
	}

	def addBob()
	{
		def bob = appService.addUser(appService.&assertUserCreationResponseDetails, "B o b", "Bob", "Dunn", "BD",
				"+$timestamp", ["iPhone 5"], [
					BudgetGoal.createNoGoInstance("news"),
					BudgetGoal.createNoGoInstance("gambling")
				])
		appService.confirmMobileNumber(appService.&assertResponseStatusSuccess, bob)
		return bob
	}

	def addRichardAndBobAsBuddies()
	{
		def richard = addRichard()
		def bob = addBob()
		appService.makeBuddies(richard, bob)
		return ["richard" : richard, "bob" : bob]
	}

	private static String createBaseTimestamp()
	{
		def formatter = new SimpleDateFormat("yyyyMMddhhmmss")
		formatter.format(new Date())
	}

	protected String getTimestamp()
	{
		int num = sequenceNumber++
		return "$baseTimestamp$num"
	}
}
