/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import groovy.json.*
import nu.yona.server.test.AdminService

class SystemAdminMessagesTest extends AbstractAppServiceIntegrationTest
{
	def AdminService adminService = new AdminService()

	def 'A system admin message gets received by both Bob and Richard'()
	{
		given:
		def richard = addRichard()
		def bob = addBob()

		when:
		adminService.postSystemMessage("Hi there!")
		def messagesRichard = appService.getMessages(richard)
		def messagesBob = appService.getMessages(bob)

		then:
		messagesRichard.status == 200
		def systemMessagesRichard = messagesRichard.responseData._embedded."yona:messages".findAll{ it."@type" == "SystemAdminMessage"}
		systemMessagesRichard.size() == 1
		systemMessagesRichard[0].message == "Hi there!"
		systemMessagesRichard[0].nickname == "(system)"
		systemMessagesRichard[0]._links?.self?.href?.startsWith(richard.messagesUrl)
		systemMessagesRichard[0]._links?.edit
		systemMessagesRichard[0]._links?."yona:markRead"
		def systemMessagesBob = messagesBob.responseData._embedded."yona:messages".findAll{ it."@type" == "SystemAdminMessage"}
		systemMessagesBob.size() == 1
		systemMessagesBob[0].message == "Hi there!"
		systemMessagesBob[0].nickname == "(system)"
		systemMessagesBob[0]._links?.self?.href?.startsWith(bob.messagesUrl)
		systemMessagesBob[0]._links?.edit
		systemMessagesBob[0]._links?."yona:markRead"
	}
}
