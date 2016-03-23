/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.test

import groovy.json.*

class BudgetGoal extends Goal
{
	final int maxDurationMinutes
	BudgetGoal(def json)
	{
		super(json)

		this.maxDurationMinutes = json.maxDurationMinutes
	}

	def convertToJsonString()
	{
		def selfLinkString = (url) ? """"_links":{"self":{"href":"$url"}},""" : ""
		return """{
			$selfLinkString,
			"@type":"BudgetGoal",
			"activityCategoryName":"${activityCategoryName}",
			"maxDurationMinutes":"${maxDurationMinutes}"
		}"""
	}

	public static BudgetGoal createNoGoInstance(activityCategoryName)
	{
		createInstance(activityCategoryName, 0)
	}

	public static BudgetGoal createInstance(activityCategoryName, maxDurationMinutes)
	{
		new BudgetGoal(["activityCategoryName": activityCategoryName, maxDurationMinutes: maxDurationMinutes])
	}
}
