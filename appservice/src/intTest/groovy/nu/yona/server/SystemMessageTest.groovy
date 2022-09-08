/*******************************************************************************
 * Copyright (c) 2017, 2022 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server


import static nu.yona.server.test.CommonAssertions.assertResponseStatus
import static nu.yona.server.test.CommonAssertions.assertResponseStatusOk

class SystemMessageTest extends AbstractAppServiceIntegrationTest
{
	def 'A system message gets received by both Bob and Richard'()
	{
		given:
		def richard = addRichard()
		def bob = addBob()
		appService.clearLastFirebaseMessage(richard.requestingDevice.firebaseInstanceId)
		appService.clearLastFirebaseMessage(bob.requestingDevice.firebaseInstanceId)

		when:
		def response = adminService.postSystemMessage("Hi there!")
		assertResponseStatus(response, 302)
		sleepTillSystemMessagesAreSent(richard)
		sleepTillSystemMessagesAreSent(bob)
		def messagesRichardResponse = appService.getMessages(richard)
		def messagesBobResponse = appService.getMessages(bob)

		then:
		assertResponseStatusOk(messagesRichardResponse)
		assertResponseStatusOk(messagesBobResponse)
		def systemMessagesRichard = messagesRichardResponse.json._embedded."yona:messages".findAll { it."@type" == "SystemMessage" }
		systemMessagesRichard.size() == 1
		systemMessagesRichard[0].message == "Hi there!"
		systemMessagesRichard[0].nickname == "Yona"
		systemMessagesRichard[0]._links.keySet() == ["self", "edit", "yona:markRead"] as Set
		systemMessagesRichard[0]._links?.self?.href?.startsWith(richard.messagesUrl)
		def messageUrlRichard = systemMessagesRichard[0]._links?.self?.href
		def systemMessagesBob = messagesBobResponse.json._embedded."yona:messages".findAll { it."@type" == "SystemMessage" }
		systemMessagesBob.size() == 1
		systemMessagesBob[0].message == "Hi there!"
		systemMessagesBob[0].nickname == "Yona"
		systemMessagesBob[0]._links.keySet() == ["self", "edit", "yona:markRead"] as Set
		systemMessagesBob[0]._links?.self?.href?.startsWith(bob.messagesUrl)
		def messageUrlBob = systemMessagesBob[0]._links?.self?.href

		def responseBob = batchService.getLastFirebaseMessage(bob.requestingDevice.firebaseInstanceId)
		assertResponseStatusOk(responseBob)
		assert responseBob.json.title == "Message received"
		assert responseBob.json.body == "Tap to open message"
		assert messageUrlBob.endsWith(responseBob.json.data.messageId.toString())

		def responseRichard = batchService.getLastFirebaseMessage(richard.requestingDevice.firebaseInstanceId)
		assertResponseStatusOk(responseRichard)
		assert responseRichard.json.title == "Message received"
		assert responseRichard.json.body == "Tap to open message"
		assert messageUrlRichard.endsWith(responseRichard.json.data.messageId.toString())

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
		sleepTillSystemMessagesAreSent(bob)
		def getMessagesResponse = appService.getMessages(bob)
		assertResponseStatusOk(getMessagesResponse)
		def systemMessagesBob = getMessagesResponse.json._embedded."yona:messages".findAll { it."@type" == "SystemMessage" }
		assert systemMessagesBob.size() == 1
		def messageDeleteUrl = systemMessagesBob[0]._links?.edit?.href

		when:
		def response = appService.deleteResourceWithPassword(messageDeleteUrl, bob.password)

		then:
		assertResponseStatusOk(response)
		appService.getMessages(richard).json._embedded."yona:messages".findAll { it."@type" == "SystemMessage" }.size() == 1
		appService.getMessages(bob).json._embedded == null

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	private void sleepTillSystemMessagesAreSent(user)
	{
		for (def i = 0; i < 10; i++)
		{
			def getUnreadMessagesResponse = appService.getMessages(user, ["onlyUnreadMessages": true,
																		  "page"              : 0,
																		  "size"              : 1])
			assertResponseStatusOk(getUnreadMessagesResponse)
			if (getUnreadMessagesResponse.json.page.totalElements > 0)
			{
				return
			}
			sleep(500)
		}
		assert false, "System message not delivered to $user.firstName in time"
	}
}
