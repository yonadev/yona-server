/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import groovy.json.*
import nu.yona.server.test.User

class UpdateBuddyTest extends AbstractAppServiceIntegrationTest
{
	def 'Update buddy and process buddy info updated message'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob

		when:
		def updatedBobJson = bob.convertToJSON()
		updatedBobJson.nickname = "Bobby"
		User bobAfterUpdate = appService.updateUser(appService.&assertUserUpdateResponseDetails, new User(updatedBobJson, bob.password))
		def richardMessagesAfterUpdate = appService.getMessages(richard)
		assert richardMessagesAfterUpdate.status == 200
		def buddyInfoUpdateMessages = richardMessagesAfterUpdate.responseData._embedded?."yona:messages".findAll{ it."@type" == "BuddyInfoChangeMessage"}

		then:
		bobAfterUpdate.nickname == "Bobby"
		bobAfterUpdate.url == bob.url

		buddyInfoUpdateMessages.size() == 1
		buddyInfoUpdateMessages[0]._links.self != null
		buddyInfoUpdateMessages[0]._links."yona:process" != null
		buddyInfoUpdateMessages[0]._links."yona:user".href == bob.url
		buddyInfoUpdateMessages[0].nickname == "BD"
		buddyInfoUpdateMessages[0].newNickname == "Bobby"
		buddyInfoUpdateMessages[0].message == "User changed nickname"

		def processResponse = appService.postMessageActionWithPassword(buddyInfoUpdateMessages[0]._links."yona:process".href, [ : ], richard.password)
		processResponse.status == 200
		processResponse.responseData.properties.status == "done"
		processResponse.responseData._embedded."yona:affectedMessages".size() == 1
		processResponse.responseData._embedded."yona:affectedMessages"[0]._links.self.href == buddyInfoUpdateMessages[0]._links.self.href
		processResponse.responseData._embedded."yona:affectedMessages"[0]._links."yona:process" == null

		User richardAfterProcess = appService.reloadUser(richard)
		richardAfterProcess.buddies[0].nickname == "Bobby"

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}
}