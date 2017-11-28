/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import groovy.json.*
import nu.yona.server.test.AppService
import nu.yona.server.test.Goal

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
		messagesRichard[0].nickname == "RQ (me)"
		messagesRichard[0]._links."yona:activityCategory".href == NEWS_ACT_CAT_URL
		messagesRichard[0].url != null
		messagesRichard[0]._links."yona:requestDisclosure" == null

		responseBob.status == 200
		def messagesBob = responseBob.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		messagesBob.size() == 1
		messagesBob[0].nickname == richard.nickname
		messagesBob[0]._links."yona:activityCategory".href == NEWS_ACT_CAT_URL
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
		def disclosureRequestUrl = goalConflictMessage._links."yona:requestDisclosure".href
		Goal goalRichard = richard.findActiveGoal(GAMBLING_ACT_CAT_URL)

		when:
		def requestMessageText = "Can I have a look?"
		def response = appService.postMessageActionWithPassword(disclosureRequestUrl, [ "message" : requestMessageText], bob.password)

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
		AppService.assertEquals(disclosureRequestMessages[0].creationTime, YonaServer.now)
		disclosureRequestMessages[0]._links?."yona:user"?.href.startsWith(YonaServer.stripQueryString(bob.url))
		disclosureRequestMessages[0]._links?.related?.href == getRichardMessagesResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}[0]._links.self.href
		disclosureRequestMessages[0]._links."yona:accept"?.href
		disclosureRequestMessages[0]._links."yona:reject"?.href
		// link to own activity present
		def dayDetailsUrlRichard = disclosureRequestMessages[0]._links."yona:dayDetails"?.href
		dayDetailsUrlRichard
		def dayActivityDetailRichard = appService.getResourceWithPassword(dayDetailsUrlRichard, richard.password)
		dayActivityDetailRichard.status == 200
		dayActivityDetailRichard.responseData.date == YonaServer.toIsoDateString(YonaServer.now)
		dayActivityDetailRichard.responseData._links."yona:goal".href == goalRichard.url

		assertMarkReadUnread(richard, disclosureRequestMessages[0])

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
		def getMessagesResponse = appService.getMessages(bob)
		assert getMessagesResponse.status == 200
		def disclosureRequestUrl = getMessagesResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}[0]._links."yona:requestDisclosure".href
		assert appService.postMessageActionWithPassword(disclosureRequestUrl, [ : ], bob.password).status == 200
		getMessagesResponse = appService.getMessages(richard)
		assert getMessagesResponse.status == 200
		def disclosureRequestMessage = getMessagesResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "DisclosureRequestMessage"}[0]
		def disclosureRequestAcceptUrl = disclosureRequestMessage._links."yona:accept".href
		bob = appService.reloadUser(bob)
		Goal goalBuddyRichard = bob.buddies[0].findActiveGoal(GAMBLING_ACT_CAT_URL)

		when:
		def responseMessageText = "Sure!"
		def response = appService.postMessageActionWithPassword(disclosureRequestAcceptUrl, [ "message" : responseMessageText ], richard.password)

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
		disclosureRequestMessages[0]._links."yona:dayDetails"?.href

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
		AppService.assertEquals(disclosureResponseMessage.creationTime, YonaServer.now)
		disclosureResponseMessage._links?.related?.href == goalConflictMessages[0]._links.self.href
		disclosureResponseMessage._links?."yona:user"?.href.startsWith(YonaServer.stripQueryString(richard.url))
		disclosureResponseMessage._embedded?."yona:user" == null
		// link to Richard's activity present
		def dayDetailsUrl = disclosureResponseMessage._links."yona:dayDetails"?.href
		dayDetailsUrl
		def dayActivityDetail = appService.getResourceWithPassword(dayDetailsUrl, bob.password)
		dayActivityDetail.status == 200
		dayActivityDetail.responseData.date == YonaServer.toIsoDateString(YonaServer.now)
		dayActivityDetail.responseData._links."yona:goal".href == goalBuddyRichard.url

		assertMarkReadUnread(bob, disclosureResponseMessage)

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
		def getMessagesResponse = appService.getMessages(bob)
		assert getMessagesResponse.status == 200
		def disclosureRequestUrl = getMessagesResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}[0]._links."yona:requestDisclosure".href
		assert appService.postMessageActionWithPassword(disclosureRequestUrl, [ : ], bob.password).status == 200
		getMessagesResponse = appService.getMessages(richard)
		assert getMessagesResponse.status == 200
		def disclosureRequestMessage = getMessagesResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "DisclosureRequestMessage"}[0]
		def disclosureRequestRejectUrl = disclosureRequestMessage._links."yona:reject".href

		when:
		def responseMessageText = "Nope!"
		def response = appService.postMessageActionWithPassword(disclosureRequestRejectUrl, [ "message" : responseMessageText ], richard.password)

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
		disclosureRequestMessages[0]._links."yona:dayDetails"?.href

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
		disclosureResponseMessage._links?."yona:user"?.href.startsWith(YonaServer.stripQueryString(richard.url))
		disclosureResponseMessage._embedded?."yona:user" == null
		disclosureResponseMessage._links."yona:dayDetails" == null

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
