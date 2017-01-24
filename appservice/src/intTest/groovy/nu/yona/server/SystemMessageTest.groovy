/*******************************************************************************
 * Copyright (c) 2017 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import groovy.json.*

class SystemMessageTest extends AbstractAppServiceIntegrationTest
{
	def 'A system message gets received by both Bob and Richard'()
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
		def systemMessagesRichard = messagesRichard.responseData._embedded."yona:messages".findAll{ it."@type" == "SystemMessage"}
		systemMessagesRichard.size() == 1
		systemMessagesRichard[0].message == "Hi there!"
		systemMessagesRichard[0].nickname == "Yona"
		systemMessagesRichard[0]._links?.self?.href?.startsWith(richard.messagesUrl)
		systemMessagesRichard[0]._links?.edit
		systemMessagesRichard[0]._links?."yona:markRead"
		def systemMessagesBob = messagesBob.responseData._embedded."yona:messages".findAll{ it."@type" == "SystemMessage"}
		systemMessagesBob.size() == 1
		systemMessagesBob[0].message == "Hi there!"
		systemMessagesBob[0].nickname == "Yona"
		systemMessagesBob[0]._links?.self?.href?.startsWith(bob.messagesUrl)
		systemMessagesBob[0]._links?.edit
		systemMessagesBob[0]._links?."yona:markRead"

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Richard deletes the system message; Bob still has it'()
	{
		given:
		def richard = addRichard()
		def bob = addBob()
		adminService.postSystemMessage("Hi there!")
		def systemMessagesBob = appService.getMessages(bob).responseData._embedded."yona:messages".findAll{ it."@type" == "SystemMessage"}
		assert systemMessagesBob.size() == 1
		def messageDeleteUrl = systemMessagesBob[0]._links?.edit?.href

		when:
		def response = appService.deleteResourceWithPassword(messageDeleteUrl, bob.password)

		then:
		response.status == 200
		appService.getMessages(richard).responseData._embedded."yona:messages".findAll{ it."@type" == "SystemMessage"}.size() == 1
		appService.getMessages(bob).responseData._embedded == null

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}
}
