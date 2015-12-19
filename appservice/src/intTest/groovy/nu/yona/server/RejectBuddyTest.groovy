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
		def rejectURL = appService.fetchBuddyConnectRequestMessage(bob).rejectURL

		when:
		def rejectMessage = "Sorry, not you"
		def rejectResponse = appService.postMessageActionWithPassword(rejectURL, ["message" : rejectMessage], bob.password)

		then:
		rejectResponse.status == 200

		// Verify connect message doesn't have actions anymore
		def actionURLs = appService.fetchBuddyConnectRequestMessage(bob).rejectURL
		!actionURLs?.size

		def processResult = appService.fetchBuddyConnectResponseMessage(richard)
		processResult.status == "REJECTED"
		processResult.message == rejectMessage

		// Have the requesting user process the buddy connect response
		def processResponse = appService.postMessageActionWithPassword(processResult.processURL, [ : ], richard.password)
		processResponse.status == 200

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
