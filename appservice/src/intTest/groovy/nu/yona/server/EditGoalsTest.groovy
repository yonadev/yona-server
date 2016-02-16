/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import groovy.json.*
import nu.yona.server.test.BudgetGoal

class EditGoalsTest extends AbstractAppServiceIntegrationTest
{
	def 'Validation: Try to add goal for not existing category'()
	{
		given:
		def richard = addRichard()
		when:
		def response = appService.addBudgetGoal(richard, BudgetGoal.createInstance("not existing", 60))

		then:
		response.status == 404
		response.responseData.code == "error.activitycategory.not.found.by.name"
	}

	def 'Validation: Try to add second goal for activity category'()
	{
		given:
		def richard = addRichard()
		when:
		def response = appService.addBudgetGoal(richard, BudgetGoal.createInstance("gambling", 60))

		then:
		response.status == 400
		response.responseData.code == "error.goal.cannot.add.second.on.activity.category"
	}

	def 'Get goals'()
	{
		given:
		def richard = addRichard()
		when:
		def response = appService.getGoals(richard)
		then:
		response.status == 200
		response.responseData._links?.self.href == richard.url + appService.GOALS_PATH_FRAGMENT
		response.responseData._embedded.budgetGoals.size() == 2
	}

	def 'Add goal'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		def richard = richardAndBob.richard
		def bob = richardAndBob.bob
		when:
		def addedGoal = appService.addBudgetGoal(appService.&assertResponseStatusCreated, richard, BudgetGoal.createInstance("social", 60), "Going to monitor my social time!")

		then:
		addedGoal

		def responseGoalsAfterAdd = appService.getGoals(richard)
		responseGoalsAfterAdd.status == 200
		responseGoalsAfterAdd.responseData._embedded.budgetGoals.size() == 3

		def bobMessagesResponse = appService.getMessages(bob)
		bobMessagesResponse.responseData._embedded.goalChangeMessages.size() == 1
		bobMessagesResponse.responseData._embedded.goalChangeMessages[0].change == 'GOAL_ADDED'
		bobMessagesResponse.responseData._embedded.goalChangeMessages[0].changedGoal.activityCategoryName == 'social'
		bobMessagesResponse.responseData._embedded.goalChangeMessages[0].user.firstName == 'Richard'
		bobMessagesResponse.responseData._embedded.goalChangeMessages[0].nickname == 'RQ'
		bobMessagesResponse.responseData._embedded.goalChangeMessages[0].message == "Going to monitor my social time!"
		bobMessagesResponse.responseData._embedded.goalChangeMessages[0]._links.edit
	}

	def 'Delete goal'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		def richard = richardAndBob.richard
		def bob = richardAndBob.bob
		def addedGoal = appService.addBudgetGoal(appService.&assertResponseStatusCreated, richard, BudgetGoal.createInstance("social", 60))
		when:
		def response = appService.removeBudgetGoal(richard, addedGoal, "Don't want to monitor my social time anymore")

		then:
		response.status == 200

		def responseGoalsAfterDelete = appService.getGoals(richard)
		responseGoalsAfterDelete.status == 200
		responseGoalsAfterDelete.responseData._embedded.budgetGoals.size() == 2

		def bobMessagesResponse = appService.getMessages(bob)
		bobMessagesResponse.responseData._embedded.goalChangeMessages.size() == 2
		bobMessagesResponse.responseData._embedded.goalChangeMessages[0].change == 'GOAL_DELETED'
		bobMessagesResponse.responseData._embedded.goalChangeMessages[0].changedGoal.activityCategoryName == 'social'
		bobMessagesResponse.responseData._embedded.goalChangeMessages[0].user.firstName == 'Richard'
		bobMessagesResponse.responseData._embedded.goalChangeMessages[0].nickname == 'RQ'
		bobMessagesResponse.responseData._embedded.goalChangeMessages[0].message == "Don't want to monitor my social time anymore"
		bobMessagesResponse.responseData._embedded.goalChangeMessages[0]._links.edit
	}
}
