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

	/*def 'Add goal'()
	 {
	 given:
	 def richard = addRichard()
	 when:
	 def addedGoal = appService.addGoal(appService.&assertResponseStatusCreated, richard, BudgetGoal.createInstance("social", 60))
	 then:
	 addedGoal
	 def responseGoalsAfterAdd = appService.getGoals(richard)
	 responseGoalsAfterAdd.status == 200
	 }
	 def 'Delete goal'()
	 {
	 given:
	 def richard = addRichard()
	 def addedGoal = appService.addGoal(appService.&assertResponseStatusCreated, richard, BudgetGoal.createInstance("social", 60))
	 when:
	 def response = appService.removeGoal(richard, addedGoal)
	 then:
	 response.status == 200
	 def responseGoalsAfterAdd = appService.getGoals(richard)
	 responseGoalsAfterAdd.status == 200
	 }*/
}
