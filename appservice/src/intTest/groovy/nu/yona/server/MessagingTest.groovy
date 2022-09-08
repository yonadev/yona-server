/*******************************************************************************
 * Copyright (c) 2015, 2022 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import static nu.yona.server.test.CommonAssertions.assertResponseStatus
import static nu.yona.server.test.CommonAssertions.assertResponseStatusOk

import nu.yona.server.test.User

class MessagingTest extends AbstractAppServiceIntegrationTest
{
	def 'Richard pages through his messages'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob
		analysisService.postToAnalysisEngine(richard.requestingDevice, ["news/media"], "http://www.refdag.nl")
		analysisService.postToAnalysisEngine(bob.requestingDevice, ["Gambling"], "http://www.poker'com")
		analysisService.postToAnalysisEngine(bob.requestingDevice, ["news/media"], "http://www.refdag.nl")

		when:
		def allMessagesResponse = appService.getMessages(richard)
		def firstPageMessagesResponse = appService.getMessages(richard, ["page": 0,
																		 "size": 2,
																		 "sort": "creationTime"])
		def secondPageMessagesResponse = appService.getMessages(richard, ["page": 1,
																		  "size": 2,
																		  "sort": "creationTime"])

		then:
		assertResponseStatusOk(allMessagesResponse)
		allMessagesResponse.json._links.self.href.startsWith(richard.messagesUrl)
		allMessagesResponse.json._links.self.href.contains("page=0")
		allMessagesResponse.json._embedded."yona:messages".findAll { it."@type" == "BuddyConnectResponseMessage" }.size() == 1
		allMessagesResponse.json._embedded."yona:messages".findAll { it."@type" == "GoalConflictMessage" }.size() == 3
		allMessagesResponse.json._embedded."yona:messages".size() == 4

		assertResponseStatusOk(firstPageMessagesResponse)
		firstPageMessagesResponse.json._links.self.href.contains("page=0")
		firstPageMessagesResponse.json._links.self.href.contains("size=2")
		firstPageMessagesResponse.json._links.self.href.contains("sort=creationTime")
		!firstPageMessagesResponse.json._links.prev
		firstPageMessagesResponse.json._links.next
		firstPageMessagesResponse.json._embedded."yona:messages".findAll { it."@type" == "BuddyConnectResponseMessage" }.size() == 0
		firstPageMessagesResponse.json._embedded."yona:messages".findAll { it."@type" == "GoalConflictMessage" }.size() == 2
		firstPageMessagesResponse.json.page.totalElements == 4

		assertResponseStatusOk(secondPageMessagesResponse)
		secondPageMessagesResponse.json._links.self.href.contains("page=1")
		secondPageMessagesResponse.json._links.self.href.contains("size=2")
		secondPageMessagesResponse.json._links.self.href.contains("sort=creationTime")
		secondPageMessagesResponse.json._links.prev
		!secondPageMessagesResponse.json._links.next
		secondPageMessagesResponse.json._embedded."yona:messages".findAll { it."@type" == "BuddyConnectResponseMessage" }.size() == 1
		secondPageMessagesResponse.json._embedded."yona:messages".findAll { it."@type" == "GoalConflictMessage" }.size() == 1
		secondPageMessagesResponse.json.page.totalElements == 4

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Richard retrieves only unread messages'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob
		analysisService.postToAnalysisEngine(richard.requestingDevice, ["Gambling"], "http://www.poker'com")
		analysisService.postToAnalysisEngine(richard.requestingDevice, ["news/media"], "http://www.refdag.nl")
		analysisService.postToAnalysisEngine(bob.requestingDevice, ["Gambling"], "http://www.poker'com")
		analysisService.postToAnalysisEngine(bob.requestingDevice, ["news/media"], "http://www.refdag.nl")

		def initialGetMessagesResponse = appService.getMessages(richard)
		assertResponseStatusOk(initialGetMessagesResponse)
		assert initialGetMessagesResponse.json.page.totalElements == 5

		markRead(richard, initialGetMessagesResponse.json._embedded."yona:messages"[0])
		markRead(richard, initialGetMessagesResponse.json._embedded."yona:messages"[2])
		markRead(richard, initialGetMessagesResponse.json._embedded."yona:messages"[3])

		when:
		def getUnreadMessagesResponse = appService.getMessages(richard, ["onlyUnreadMessages": true])

		then:
		assertResponseStatusOk(getUnreadMessagesResponse)
		getUnreadMessagesResponse.json.page.totalElements == 2

		getUnreadMessagesResponse.json._embedded."yona:messages"[0]._links.self.href == initialGetMessagesResponse.json._embedded."yona:messages"[1]._links.self.href
		getUnreadMessagesResponse.json._embedded."yona:messages"[1]._links.self.href == initialGetMessagesResponse.json._embedded."yona:messages"[4]._links.self.href

		def secondGetMessagesResponse = appService.getMessages(richard, ["onlyUnreadMessages": false])
		secondGetMessagesResponse.json.page.totalElements == 5

		markUnread(richard, secondGetMessagesResponse.json._embedded."yona:messages"[2])

		def secondGetUnreadMessagesResponse = appService.getMessages(richard, ["onlyUnreadMessages": true])
		assertResponseStatusOk(secondGetUnreadMessagesResponse)
		secondGetUnreadMessagesResponse.json.page.totalElements == 3

		secondGetUnreadMessagesResponse.json._embedded."yona:messages"[0]._links.self.href == initialGetMessagesResponse.json._embedded."yona:messages"[1]._links.self.href
		secondGetUnreadMessagesResponse.json._embedded."yona:messages"[1]._links.self.href == initialGetMessagesResponse.json._embedded."yona:messages"[2]._links.self.href
		secondGetUnreadMessagesResponse.json._embedded."yona:messages"[2]._links.self.href == initialGetMessagesResponse.json._embedded."yona:messages"[4]._links.self.href

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Bob tries to delete Richard\'s buddy request before it is processed'()
	{
		given:
		User richard = addRichard()
		User bob = addBob()
		bob.emailAddress = "bob@dunn.net"
		appService.sendBuddyConnectRequest(richard, bob)
		def messageUrl = appService.getMessages(bob).json._embedded."yona:messages".findAll { it."@type" == "BuddyConnectRequestMessage" }[0]._links.self.href

		when:
		def response = appService.deleteResourceWithPassword(messageUrl, bob.password)

		then:
		assertResponseStatus(response, 400)
		response.json?.code == "error.cannot.delete.unprocessed.message"
		def buddyConnectRequestMessages = appService.getMessages(bob).json._embedded."yona:messages".findAll { it."@type" == "BuddyConnectRequestMessage" }
		buddyConnectRequestMessages.size() == 1
		!buddyConnectRequestMessages[0]._links.edit

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Bob deletes Richard\'s buddy request after it is processed'()
	{
		given:
		User richard = addRichard()
		User bob = addBob()
		bob.emailAddress = "bob@dunn.net"
		appService.sendBuddyConnectRequest(richard, bob)
		String acceptUrl = appService.fetchBuddyConnectRequestMessage(bob).acceptUrl
		appService.postMessageActionWithPassword(acceptUrl, ["message": "Yes, great idea!"], bob.password)
		def messageDeleteUrl = appService.getMessages(bob).json._embedded."yona:messages".findAll { it."@type" == "BuddyConnectRequestMessage" }[0]._links.edit.href

		when:
		def response = appService.deleteResourceWithPassword(messageDeleteUrl, bob.password)

		then:
		assertResponseStatusOk(response)
		!appService.getMessages(bob).json._embedded?."yona:messages"?.size()

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Richard deletes Bob\'s buddy acceptance after it is processed'()
	{
		given:
		User richard = addRichard()
		User bob = addBob()
		bob.emailAddress = "bob@dunn.net"
		appService.sendBuddyConnectRequest(richard, bob)
		String acceptUrl = appService.fetchBuddyConnectRequestMessage(bob).acceptUrl
		appService.postMessageActionWithPassword(acceptUrl, ["message": "Yes, great idea!"], bob.password)
		appService.fetchBuddyConnectResponseMessage(richard).processUrl == null // Processing happens automatically these days
		def messageDeleteUrl = appService.getMessages(richard).json._embedded."yona:messages".findAll { it."@type" == "BuddyConnectResponseMessage" }[0]._links.edit.href

		when:
		def response = appService.deleteResourceWithPassword(messageDeleteUrl, richard.password)

		then:
		assertResponseStatusOk(response)
		!appService.getMessages(richard).json._embedded?."yona:messages"?.size()

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Richard deletes a goal conflict message. After that, it\'s gone for Bob too'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob
		analysisService.postToAnalysisEngine(richard.requestingDevice, ["news/media"], "http://www.refdag.nl")
		def messageDeleteUrl = appService.getMessages(richard).json._embedded."yona:messages".findAll { it."@type" == "GoalConflictMessage" }[0]._links.edit.href

		when:
		def response = appService.deleteResourceWithPassword(messageDeleteUrl, richard.password)

		then:
		assertResponseStatusOk(response)
		appService.getMessages(richard).json._embedded?."yona:messages"?.findAll { it."@type" == "GoalConflictMessage" }?.size() == 0

		appService.getMessages(bob).json._embedded."yona:messages".findAll { it."@type" == "GoalConflictMessage" }.size() == 0

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Bob deletes a goal conflict message. After that, Richard still has it'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob
		analysisService.postToAnalysisEngine(richard.requestingDevice, ["news/media"], "http://www.refdag.nl")
		def messageDeleteUrl = appService.getMessages(bob).json._embedded."yona:messages".findAll { it."@type" == "GoalConflictMessage" }[0]._links.edit.href

		when:
		def response = appService.deleteResourceWithPassword(messageDeleteUrl, bob.password)

		then:
		assertResponseStatusOk(response)
		appService.getMessages(richard).json._embedded."yona:messages".findAll { it."@type" == "GoalConflictMessage" }.size() == 1

		appService.getMessages(bob).json._embedded?."yona:messages"?.findAll { it."@type" == "GoalConflictMessage" }?.size() == 0

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	private void markRead(User user, def message)
	{
		String markReadUrl = message._links?."yona:markRead"?.href
		assert markReadUrl
		appService.postMessageActionWithPassword(markReadUrl, [:], user.password)
	}

	private void markUnread(User user, def message)
	{
		String markUnreadUrl = message._links?."yona:markUnread"?.href
		assert markUnreadUrl
		def response = appService.postMessageActionWithPassword(markUnreadUrl, [:], user.password)
		assertResponseStatusOk(response)
	}
}
