/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import static nu.yona.server.test.CommonAssertions.*

import groovy.json.*

class RejectBuddyTest extends AbstractAppServiceIntegrationTest
{
	def 'Bob rejects Richard\'s buddy request'()
	{
		given:
		def richard = addRichard()
		def bob = addBob()
		bob.emailAddress = "bob@dunn.net"
		appService.sendBuddyConnectRequest(richard, bob)
		def connectRequestMessage = appService.fetchBuddyConnectRequestMessage(bob)
		def rejectUrl = connectRequestMessage.rejectUrl

		when:
		def rejectMessage = "Sorry, not you"
		def rejectResponse = appService.postMessageActionWithPassword(rejectUrl, ["message" : rejectMessage], bob.password)

		then:
		assertResponseStatusOk(rejectResponse)
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
		processResult.processUrl == null // Processing happens automatically these days

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
