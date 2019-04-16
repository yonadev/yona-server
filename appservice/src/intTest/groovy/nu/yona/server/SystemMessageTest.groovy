/*******************************************************************************
 * Copyright (c) 2017, 2019 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import static nu.yona.server.test.CommonAssertions.*

class SystemMessageTest extends AbstractAppServiceIntegrationTest
{
	def 'A system message gets received by both Bob and Richard'()
	{
		given:
		def richard = addRichard()
		def bob = addBob()

		when:
		def response = adminService.postSystemMessage("Hi there!")
		sleepTillSystemMessagesAreSent(richard)
		def messagesRichard = appService.getMessages(richard)
		def messagesBob = appService.getMessages(bob)

		then:
		assertResponseStatus(response, 302)
		assertResponseStatusOk(messagesRichard)
		def systemMessagesRichard = messagesRichard.responseData._embedded."yona:messages".findAll{ it."@type" == "SystemMessage"}
		systemMessagesRichard.size() == 1
		systemMessagesRichard[0].message == "Hi there!"
		systemMessagesRichard[0].nickname == "Yona"
		systemMessagesRichard[0]._links.keySet() == ["self", "edit", "yona:markRead"] as Set
		systemMessagesRichard[0]._links?.self?.href?.startsWith(richard.messagesUrl)
		def systemMessagesBob = messagesBob.responseData._embedded."yona:messages".findAll{ it."@type" == "SystemMessage"}
		systemMessagesBob.size() == 1
		systemMessagesBob[0].message == "Hi there!"
		systemMessagesBob[0].nickname == "Yona"
		systemMessagesBob[0]._links.keySet() == ["self", "edit", "yona:markRead"] as Set
		systemMessagesBob[0]._links?.self?.href?.startsWith(bob.messagesUrl)

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Richard deletes the system message; Bob still has it'()
	{
		given:
		def richard = addRichard()
		def bob = addBob()
		def postResponse = adminService.postSystemMessage("Hi there!")
		assertResponseStatus(postResponse, 302)
		sleepTillSystemMessagesAreSent(richard)
		def systemMessagesBob = appService.getMessages(bob).responseData._embedded."yona:messages".findAll{ it."@type" == "SystemMessage"}
		assert systemMessagesBob.size() == 1
		def messageDeleteUrl = systemMessagesBob[0]._links?.edit?.href

		when:
		def response = appService.deleteResourceWithPassword(messageDeleteUrl, bob.password)

		then:
		assertResponseStatusOk(response)
		appService.getMessages(richard).responseData._embedded."yona:messages".findAll{ it."@type" == "SystemMessage"}.size() == 1
		appService.getMessages(bob).responseData._embedded == null

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	private void sleepTillSystemMessagesAreSent(user)
	{
		for (def i = 0; i <10; i++)
		{
			def getUnreadMessagesResponse = appService.getMessages(user, [ "onlyUnreadMessages" : true,
				"page": 0,
				"size": 1])
			assertResponseStatusOk(getUnreadMessagesResponse)
			if (getUnreadMessagesResponse.responseData.page.totalElements > 0)
			{
				return
			}
			sleep(500)
		}
		assert false, "System message not delivered in time"
	}
}
