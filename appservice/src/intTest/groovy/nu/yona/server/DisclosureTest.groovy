/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import groovy.json.*

class DisclosureTest extends AbstractAppServiceIntegrationTest
{
	def 'Disclosure link is available to buddy, not to <self>'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		def richard = richardAndBob.richard
		def bob = richardAndBob.bob
		analysisService.postToAnalysisEngine(richard, ["news/media"], "http://www.refdag.nl")

		when:
		def responseRichard = appService.getMessages(richard)
		def responseBob = appService.getMessages(bob)

		then:
		responseRichard.status == 200
		def messagesRichard = responseRichard.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		messagesRichard.size() == 1
		messagesRichard[0].nickname == "<self>"
		messagesRichard[0].activityCategoryName == "news"
		messagesRichard[0].url != null
		messagesRichard[0]._links."yona:requestDisclosure" == null

		responseBob.status == 200
		def messagesBob = responseBob.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		messagesBob.size() == 1
		messagesBob[0].nickname == richard.nickname
		messagesBob[0].activityCategoryName == "news"
		messagesBob[0].url == null
		messagesBob[0]._links."yona:requestDisclosure"

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Richard receives Bob\'s request for disclosure of a message'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		def richard = richardAndBob.richard
		def bob = richardAndBob.bob
		analysisService.postToAnalysisEngine(richard, ["Gambling"], "http://www.poker.com")
		def goalConflictMessage = appService.getMessages(bob).responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}[0]
		def disclosureRequestURL = goalConflictMessage._links."yona:requestDisclosure".href

		when:
		def requestMessageText = "Can I have a look?"
		def response = appService.postMessageActionWithPassword(disclosureRequestURL, [ "message" : requestMessageText], bob.password)

		then:
		response.status == 200
		response.responseData.properties.status == "done"
		response.responseData._embedded."yona:affectedMessages".size() == 1
		response.responseData._embedded."yona:affectedMessages"[0]._links.self.href == goalConflictMessage._links.self.href
		response.responseData._embedded."yona:affectedMessages"[0].status == "DISCLOSURE_REQUESTED"
		response.responseData._embedded."yona:affectedMessages"[0]._links.requestDisclosure == null

		def getRichardMessagesResponse = appService.getMessages(richard)
		getRichardMessagesResponse.status == 200
		def disclosureRequestMessages = getRichardMessagesResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "DisclosureRequestMessage"}
		disclosureRequestMessages.size() == 1
		disclosureRequestMessages[0].status == "DISCLOSURE_REQUESTED"
		disclosureRequestMessages[0].message == requestMessageText
		assertEquals(disclosureRequestMessages[0].creationTime, new Date())
		disclosureRequestMessages[0]._links?."yona:user"?.href== bob.url
		disclosureRequestMessages[0]._links?.related?.href == getRichardMessagesResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}[0]._links.self.href
		disclosureRequestMessages[0]._links."yona:accept"?.href
		disclosureRequestMessages[0]._links."yona:reject"?.href

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Richard accepts Bob\'s request for disclosure of a message'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		def richard = richardAndBob.richard
		def bob = richardAndBob.bob
		analysisService.postToAnalysisEngine(richard, ["Gambling"], "http://www.poker.com")
		def disclosureRequestURL = appService.getMessages(bob).responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}[0]._links."yona:requestDisclosure".href
		appService.postMessageActionWithPassword(disclosureRequestURL, [ : ], bob.password)
		def disclosureRequestMessage = appService.getMessages(richard).responseData._embedded."yona:messages".findAll{ it."@type" == "DisclosureRequestMessage"}[0]
		def disclosureRequestAcceptURL = disclosureRequestMessage._links."yona:accept".href

		when:
		def responseMessageText = "Sure!"
		def response = appService.postMessageActionWithPassword(disclosureRequestAcceptURL, [ "message" : responseMessageText ], richard.password)

		then:
		response.status == 200
		response.responseData._embedded."yona:affectedMessages".size() == 1
		response.responseData._embedded."yona:affectedMessages"[0]._links.self.href == disclosureRequestMessage._links.self.href
		response.responseData._embedded."yona:affectedMessages"[0].status == "DISCLOSURE_ACCEPTED"
		response.responseData._embedded."yona:affectedMessages"[0]._links."yona:accept" == null
		response.responseData._embedded."yona:affectedMessages"[0]._links."yona:reject" == null

		def getRichardMessagesResponse = appService.getMessages(richard)
		getRichardMessagesResponse.status == 200
		def disclosureRequestMessages = getRichardMessagesResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "DisclosureRequestMessage"}
		disclosureRequestMessages.size() == 1
		disclosureRequestMessages[0].status == "DISCLOSURE_ACCEPTED"
		disclosureRequestMessages[0]._links."yona:accept" == null
		disclosureRequestMessages[0]._links."yona:reject" == null

		def getBobMessagesResponse = appService.getMessages(bob)
		getBobMessagesResponse.status == 200
		def goalConflictMessages = getBobMessagesResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		goalConflictMessages.size() == 1
		goalConflictMessages[0].url == "http://www.poker.com"
		goalConflictMessages[0].status == "DISCLOSURE_ACCEPTED"

		//check disclosure response message
		def disclosureResponseMessage = getBobMessagesResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "DisclosureResponseMessage"}[0]
		disclosureResponseMessage.status == "DISCLOSURE_ACCEPTED"
		disclosureResponseMessage.message == responseMessageText
		disclosureResponseMessage.nickname == richard.nickname
		assertEquals(disclosureResponseMessage.creationTime, new Date())
		disclosureResponseMessage._links?.related?.href == goalConflictMessages[0]._links.self.href
		disclosureResponseMessage._links?."yona:user"?.href == richard.url

		//check delete
		disclosureResponseMessage._links.edit
		def deleteResponse = appService.deleteResourceWithPassword(disclosureResponseMessage._links.edit.href, bob.password)
		deleteResponse.status == 200
		def getBobMessagesResponseAfterDelete = appService.getMessages(bob)
		!getBobMessagesResponseAfterDelete.responseData._embedded."yona:disclosureResponseMessages"

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Richard rejects Bob\'s request for disclosure of a message'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		def richard = richardAndBob.richard
		def bob = richardAndBob.bob
		analysisService.postToAnalysisEngine(richard, ["Gambling"], "http://www.poker.com")
		def disclosureRequestURL = appService.getMessages(bob).responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}[0]._links."yona:requestDisclosure".href
		appService.postMessageActionWithPassword(disclosureRequestURL, [ : ], bob.password)
		def disclosureRequestMessage = appService.getMessages(richard).responseData._embedded."yona:messages".findAll{ it."@type" == "DisclosureRequestMessage"}[0]
		def disclosureRequestRejectURL = disclosureRequestMessage._links."yona:reject".href

		when:
		def responseMessageText = "Nope!"
		def response = appService.postMessageActionWithPassword(disclosureRequestRejectURL, [ "message" : responseMessageText ], richard.password)

		then:
		response.status == 200
		response.responseData._embedded."yona:affectedMessages".size() == 1
		response.responseData._embedded."yona:affectedMessages"[0]._links.self.href == disclosureRequestMessage._links.self.href
		response.responseData._embedded."yona:affectedMessages"[0].status == "DISCLOSURE_REJECTED"
		response.responseData._embedded."yona:affectedMessages"[0]._links."yona:accept" == null
		response.responseData._embedded."yona:affectedMessages"[0]._links."yona:reject" == null

		def getRichardMessagesResponse = appService.getMessages(richard)
		getRichardMessagesResponse.status == 200
		def disclosureRequestMessages = getRichardMessagesResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "DisclosureRequestMessage"}
		disclosureRequestMessages.size() == 1
		disclosureRequestMessages[0].status == "DISCLOSURE_REJECTED"
		disclosureRequestMessages[0]._links."yona:accept" == null
		disclosureRequestMessages[0]._links."yona:reject" == null

		def getBobMessagesResponse = appService.getMessages(bob)
		getBobMessagesResponse.status == 200
		def goalConflictMessages = getBobMessagesResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		goalConflictMessages.size() == 1
		goalConflictMessages[0].url == null
		goalConflictMessages[0].status == "DISCLOSURE_REJECTED"

		//check disclosure response message
		def disclosureResponseMessage = getBobMessagesResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "DisclosureResponseMessage"}[0]
		disclosureResponseMessage.status == "DISCLOSURE_REJECTED"
		disclosureResponseMessage.message == responseMessageText
		disclosureResponseMessage.nickname == richard.nickname
		disclosureResponseMessage._links?.related?.href == goalConflictMessages[0]._links.self.href
		disclosureResponseMessage._links?."yona:user"?.href == richard.url

		//check delete
		disclosureResponseMessage._links.edit
		def deleteResponse = appService.deleteResourceWithPassword(disclosureResponseMessage._links.edit.href, bob.password)
		deleteResponse.status == 200
		def getBobMessagesResponseAfterDelete = appService.getMessages(bob)
		getBobMessagesResponseAfterDelete.responseData._embedded."yona:messages".findAll{ it."@type" == "DiscloseResponseMessage"}.size() == 0

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}
}
