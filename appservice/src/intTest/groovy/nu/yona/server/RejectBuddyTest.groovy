/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import groovy.json.*

class RejectBuddyTest extends AbstractAppServiceIntegrationTest
{
	def 'Bob rejects Richard\'s buddy request'()
	{
		given:
		def richard = addRichard()
		def bob = addBob()
		appService.sendBuddyConnectRequest(richard, bob)
		def connectRequestMessage = appService.fetchBuddyConnectRequestMessage(bob)
		def rejectUrl = connectRequestMessage.rejectUrl

		when:
		def rejectMessage = "Sorry, not you"
		def rejectResponse = appService.postMessageActionWithPassword(rejectUrl, ["message" : rejectMessage], bob.password)

		then:
		rejectResponse.status == 200
		rejectResponse.responseData._embedded."yona:affectedMessages".size() == 1
		rejectResponse.responseData._embedded."yona:affectedMessages"[0]._links.self.href == connectRequestMessage.selfUrl
		rejectResponse.responseData._embedded."yona:affectedMessages"[0].status == "REJECTED"
		rejectResponse.responseData._embedded."yona:affectedMessages"[0]._links."yona:accept" == null
		rejectResponse.responseData._embedded."yona:affectedMessages"[0]._links."yona:reject" == null

		// Verify connect message doesn't have actions anymore
		def actionUrls = appService.fetchBuddyConnectRequestMessage(bob).rejectUrl
		!actionUrls?.size

		def processResult = appService.fetchBuddyConnectResponseMessage(richard)
		processResult.status == "REJECTED"
		processResult.message == rejectMessage

		// Have the requesting user process the buddy connect response
		def processResponse = appService.postMessageActionWithPassword(processResult.processUrl, [ : ], richard.password)
		processResponse.status == 200
		processResponse.responseData._embedded."yona:affectedMessages".size() == 1
		processResponse.responseData._embedded."yona:affectedMessages"[0]._links.self.href == processResult.selfUrl
		processResponse.responseData._embedded."yona:affectedMessages"[0]._links."yona:process" == null

		// Verify that Bob is not in Richard's buddy list anymore
		def buddiesRichard = appService.getBuddies(richard)
		buddiesRichard.size() == 0

		// Verify that Richard is not in Bob's buddy list anymore
		def buddiesBob = appService.getBuddies(bob)
		buddiesBob.size() == 0

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}
}
