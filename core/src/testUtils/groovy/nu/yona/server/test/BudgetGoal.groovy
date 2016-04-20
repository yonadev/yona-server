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
		def selfLinkString = (url) ? """"
							"self":
								{
									"href":"$url"
								},""" : ""
		return """{
			"@type":"BudgetGoal",
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

	public static BudgetGoal createNoGoInstance(activityCategoryUrl)
	{
		createInstance(activityCategoryUrl, 0)
	}

	public static BudgetGoal createInstance(activityCategoryUrl, maxDurationMinutes)
	{
		assert activityCategoryUrl
		new BudgetGoal(["activityCategoryUrl": activityCategoryUrl, maxDurationMinutes: maxDurationMinutes])
	}
}
