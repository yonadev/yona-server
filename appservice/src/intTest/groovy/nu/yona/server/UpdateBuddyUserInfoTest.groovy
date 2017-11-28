/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import groovy.json.*
import nu.yona.server.test.User

class UpdateBuddyUserInfoTest extends AbstractAppServiceIntegrationTest
{
	def 'Buddy updates info which causes buddy info change message and nickname update on process'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob

		when:
		def updatedBobJson = bob.convertToJson()
		updatedBobJson.nickname = "Bobby"
		User bobAfterUpdate = appService.updateUser(appService.&assertUserUpdateResponseDetails, new User(updatedBobJson))
		def richardMessagesAfterUpdate = appService.getMessages(richard)
		assert richardMessagesAfterUpdate.status == 200
		def buddyInfoUpdateMessages = richardMessagesAfterUpdate.responseData._embedded?."yona:messages".findAll{ it."@type" == "BuddyInfoChangeMessage"}

		then:
		bobAfterUpdate.nickname == "Bobby"
		bobAfterUpdate.url == bob.url

		buddyInfoUpdateMessages.size() == 1
		buddyInfoUpdateMessages[0]._links.self != null
		buddyInfoUpdateMessages[0]._links."yona:process" == null // Processing happens automatically these days
		buddyInfoUpdateMessages[0]._links."yona:buddy".href == richard.buddies[0].url
		buddyInfoUpdateMessages[0].nickname == "Bobby"
		buddyInfoUpdateMessages[0].message == "User changed personal info"

		User richardAfterProcess = appService.reloadUser(richard)
		richardAfterProcess.buddies[0].nickname == "Bobby"
		richardAfterProcess.buddies[0].user.nickname == "Bobby"

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Buddy changes first and last name which causes buddy info change message too'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob

		when:
		def updatedBobJson = bob.convertToJson()
		updatedBobJson.firstName = "Robert"
		updatedBobJson.lastName = "Dunstan"
		User bobAfterUpdate = appService.updateUser(appService.&assertUserUpdateResponseDetails, new User(updatedBobJson))
		def richardMessagesAfterUpdate = appService.getMessages(richard)
		assert richardMessagesAfterUpdate.status == 200
		def buddyInfoUpdateMessages = richardMessagesAfterUpdate.responseData._embedded?."yona:messages".findAll{ it."@type" == "BuddyInfoChangeMessage"}

		then:
		buddyInfoUpdateMessages.size() == 1
		buddyInfoUpdateMessages[0]._links.self != null
		buddyInfoUpdateMessages[0]._links."yona:process" == null // Processing happens automatically these days
		buddyInfoUpdateMessages[0]._links."yona:user".href.startsWith(YonaServer.stripQueryString(bob.url))
		buddyInfoUpdateMessages[0]._links."yona:buddy".href == richard.buddies[0].url
		buddyInfoUpdateMessages[0].nickname == "BD"
		buddyInfoUpdateMessages[0].message == "User changed personal info"

		User richardAfterProcess = appService.reloadUser(richard)
		richardAfterProcess.buddies[0].nickname == "BD"

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}
}
