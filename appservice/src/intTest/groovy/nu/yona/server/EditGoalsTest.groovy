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
		def response = appService.addGoal(richard, BudgetGoal.createInstance("not existing", 60))

		then:
		response.status == 404
		response.responseData.code == "error.activitycategory.not.found.by.name"
	}

	def 'Validation: Try to add second goal for activity category'()
	{
		given:
		def richard = addRichard()
		when:
		def response = appService.addGoal(richard, BudgetGoal.createInstance("gambling", 60))

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
		response.responseData._embedded."yona:goals".size() == 2
		def gamblingGoals = response.responseData._embedded."yona:goals".findAll{ it.activityCategoryName == 'gambling'}
		gamblingGoals.size() == 1
		gamblingGoals[0]."@type" == "BudgetGoal"
		!gamblingGoals[0]._links.edit //mandatory goal
		def newsGoals = response.responseData._embedded."yona:goals".findAll{ it.activityCategoryName == 'news'}
		newsGoals.size() == 1
		newsGoals[0]."@type" == "BudgetGoal"
		newsGoals[0]._links.edit.href
	}

	def 'Add goal'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		def richard = richardAndBob.richard
		def bob = richardAndBob.bob
		when:
		def addedGoal = appService.addGoal(appService.&assertResponseStatusCreated, richard, BudgetGoal.createInstance("social", 60), "Going to monitor my social time!")

		then:
		addedGoal

		def responseGoalsAfterAdd = appService.getGoals(richard)
		responseGoalsAfterAdd.status == 200
		responseGoalsAfterAdd.responseData._embedded."yona:goals".size() == 3

		def bobMessagesResponse = appService.getMessages(bob)
		def goalChangeMessages = bobMessagesResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalChangeMessage"}
		goalChangeMessages.size() == 1
		goalChangeMessages[0].change == 'GOAL_ADDED'
		goalChangeMessages[0].changedGoal.activityCategoryName == 'social'
		goalChangeMessages[0].user.firstName == 'Richard'
		goalChangeMessages[0].nickname == 'RQ'
		assertEquals(goalChangeMessages[0].creationTime, new Date())
		goalChangeMessages[0].message == "Going to monitor my social time!"
		goalChangeMessages[0]._links.edit
	}

	def 'Delete goal'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		def richard = richardAndBob.richard
		def bob = richardAndBob.bob
		def addedGoal = appService.addGoal(appService.&assertResponseStatusCreated, richard, BudgetGoal.createInstance("social", 60))
		when:
		def response = appService.removeGoal(richard, addedGoal, "Don't want to monitor my social time anymore")

		then:
		response.status == 200

		def responseGoalsAfterDelete = appService.getGoals(richard)
		responseGoalsAfterDelete.status == 200
		responseGoalsAfterDelete.responseData._embedded."yona:goals".size() == 2

		def bobMessagesResponse = appService.getMessages(bob)
		def goalChangeMessages = bobMessagesResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalChangeMessage"}
		goalChangeMessages.size() == 2
		goalChangeMessages[0].change == 'GOAL_DELETED'
		goalChangeMessages[0].changedGoal.activityCategoryName == 'social'
		goalChangeMessages[0].user.firstName == 'Richard'
		goalChangeMessages[0].nickname == 'RQ'
		assertEquals(goalChangeMessages[0].creationTime, new Date())
		goalChangeMessages[0].message == "Don't want to monitor my social time anymore"
		goalChangeMessages[0]._links.edit
	}

	def 'Validation: Try to remove mandatory goal'()
	{
		given:
		def richard = addRichard()
		when:
		def response = appService.deleteResourceWithPassword(richard.goals[0].url, richard.password)
		then:
		response.status == 400
		def responseGoalsAfterDelete = appService.getGoals(richard)
		responseGoalsAfterDelete.status == 200
		responseGoalsAfterDelete.responseData._embedded."yona:goals".size() == 2
	}
}
