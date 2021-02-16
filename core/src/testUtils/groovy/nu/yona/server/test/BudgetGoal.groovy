/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.test

import java.time.ZonedDateTime

import groovy.transform.ToString

@ToString(includeSuper = true, includeNames = true)
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
		def selfLinkString = buildSelfLinkString()
		def creationTimeString = buildCreationTimeString()
		return """{
			"@type":"BudgetGoal",
			$creationTimeString
			"maxDurationMinutes":"${maxDurationMinutes}",
			"_links":
				{
					$selfLinkString
					"yona:activityCategory":
						{
							"href":"$activityCategoryUrl"
						}
				}
		}"""
	}

	static BudgetGoal createNoGoInstance(activityCategoryUrl)
	{
		createInstance(activityCategoryUrl, 0)
	}

	static BudgetGoal createInstance(String activityCategoryUrl, maxDurationMinutes)
	{
		createInstance(null, activityCategoryUrl, maxDurationMinutes)
	}

	static BudgetGoal createInstance(ZonedDateTime creationTime, activityCategoryUrl, maxDurationMinutes)
	{
		assert activityCategoryUrl
		new BudgetGoal([creationTime: creationTime, activityCategoryUrl: activityCategoryUrl, maxDurationMinutes: maxDurationMinutes])
	}

	static BudgetGoal createInstance(BudgetGoal originalGoal, ZonedDateTime creationTime, int maxDurationMinutes)
	{
		new BudgetGoal([creationTime: creationTime, activityCategoryUrl: originalGoal.activityCategoryUrl, maxDurationMinutes: maxDurationMinutes])
	}
}
