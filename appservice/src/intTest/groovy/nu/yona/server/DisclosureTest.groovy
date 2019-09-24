/*******************************************************************************
 * Copyright (c) 2015, 2019 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import static nu.yona.server.test.CommonAssertions.*

import nu.yona.server.test.Goal
import nu.yona.server.test.User

class DisclosureTest extends AbstractAppServiceIntegrationTest
{
	def 'Disclosure link is available to buddy, not to <self>'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob
		analysisService.postToAnalysisEngine(richard.requestingDevice, ["news/media"], "http://www.refdag.nl")

		when:
		def responseRichard = appService.getMessages(richard)
		def responseBob = appService.getMessages(bob)

		then:
		assertResponseStatusOk(responseRichard)
		def messagesRichard = responseRichard.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		messagesRichard.size() == 1
		messagesRichard[0].nickname == "RQ (me)"
		messagesRichard[0]._links.keySet() == ["self", "edit", "yona:activityCategory", "yona:dayDetails", "yona:markRead"] as Set
		messagesRichard[0]._links."yona:activityCategory".href == NEWS_ACT_CAT_URL
		messagesRichard[0].url != null

		assertResponseStatusOk(responseBob)
		def messagesBob = responseBob.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		messagesBob.size() == 1
		messagesBob[0].nickname == richard.nickname
		messagesBob[0]._links.keySet() == ["self", "edit", "yona:buddy", "yona:activityCategory", "yona:dayDetails", "yona:requestDisclosure", "yona:markRead"] as Set
		messagesBob[0]._links."yona:activityCategory".href == NEWS_ACT_CAT_URL
		messagesBob[0].url == null

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Richard receives Bob\'s request for disclosure of a message'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob
		analysisService.postToAnalysisEngine(richard.requestingDevice, ["Gambling"], "http://www.poker.com")
		def goalConflictMessage = appService.getMessages(bob).responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}[0]
		def disclosureRequestUrl = goalConflictMessage._links."yona:requestDisclosure".href
		Goal goalRichard = richard.findActiveGoal(GAMBLING_ACT_CAT_URL)

		when:
		def requestMessageText = "Can I have a look?"
		def response = appService.postMessageActionWithPassword(disclosureRequestUrl, [ "message" : requestMessageText], bob.password)

		then:
		assertResponseStatusOk(response)
		response.responseData.properties.status == "done"
		response.responseData._embedded."yona:affectedMessages".size() == 1
		response.responseData._embedded."yona:affectedMessages"[0]._links.self.href == goalConflictMessage._links.self.href
		response.responseData._embedded."yona:affectedMessages"[0].status == "DISCLOSURE_REQUESTED"
		response.responseData._embedded."yona:affectedMessages"[0]._links.requestDisclosure == null

		def getRichardMessagesResponse = appService.getMessages(richard)
		assertResponseStatusOk(getRichardMessagesResponse)
		def disclosureRequestMessages = getRichardMessagesResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "DisclosureRequestMessage"}
		disclosureRequestMessages.size() == 1
		disclosureRequestMessages[0].status == "DISCLOSURE_REQUESTED"
		disclosureRequestMessages[0].message == requestMessageText
		assertEquals(disclosureRequestMessages[0].creationTime, YonaServer.now)
		disclosureRequestMessages[0]._links.keySet() == ["self", "related", "yona:buddy", "yona:user", "yona:accept", "yona:reject", "yona:markRead", "yona:dayDetails"] as Set
		disclosureRequestMessages[0]._links?."yona:user"?.href.startsWith(YonaServer.stripQueryString(bob.url))
		disclosureRequestMessages[0]._links?.related?.href == getRichardMessagesResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}[0]._links.self.href
		// link to own activity present
		def dayDetailsUrlRichard = disclosureRequestMessages[0]._links."yona:dayDetails"?.href
		def dayActivityDetailRichard = appService.getResourceWithPassword(dayDetailsUrlRichard, richard.password)
		assertResponseStatusOk(dayActivityDetailRichard)
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
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob
		analysisService.postToAnalysisEngine(richard.requestingDevice, ["Gambling"], "http://www.poker.com")
		def getMessagesResponse = appService.getMessages(bob)
		assertResponseStatusOk(getMessagesResponse)
		def disclosureRequestUrl = getMessagesResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}[0]._links."yona:requestDisclosure".href
		assertResponseStatusOk(appService.postMessageActionWithPassword(disclosureRequestUrl, [ : ], bob.password))
		getMessagesResponse = appService.getMessages(richard)
		assertResponseStatusOk(getMessagesResponse)
		def disclosureRequestMessage = getMessagesResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "DisclosureRequestMessage"}[0]
		def disclosureRequestAcceptUrl = disclosureRequestMessage._links."yona:accept".href
		bob = appService.reloadUser(bob)
		Goal goalBuddyRichard = bob.buddies[0].findActiveGoal(GAMBLING_ACT_CAT_URL)

		when:
		def responseMessageText = "Sure!"
		def response = appService.postMessageActionWithPassword(disclosureRequestAcceptUrl, [ "message" : responseMessageText ], richard.password)

		then:
		assertResponseStatusOk(response)
		response.responseData._embedded."yona:affectedMessages".size() == 1
		response.responseData._embedded."yona:affectedMessages"[0]._links.self.href == disclosureRequestMessage._links.self.href
		response.responseData._embedded."yona:affectedMessages"[0].status == "DISCLOSURE_ACCEPTED"
		response.responseData._embedded."yona:affectedMessages"[0]._links."yona:accept" == null
		response.responseData._embedded."yona:affectedMessages"[0]._links."yona:reject" == null

		def getRichardMessagesResponse = appService.getMessages(richard)
		assertResponseStatusOk(getRichardMessagesResponse)
		def disclosureRequestMessages = getRichardMessagesResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "DisclosureRequestMessage"}
		disclosureRequestMessages.size() == 1
		disclosureRequestMessages[0].status == "DISCLOSURE_ACCEPTED"
		disclosureRequestMessages[0]._links.keySet() == ["self", "edit", "related", "yona:buddy", "yona:user", "yona:markRead", "yona:dayDetails"] as Set

		def getBobMessagesResponse = appService.getMessages(bob)
		assertResponseStatusOk(getBobMessagesResponse)
		def goalConflictMessages = getBobMessagesResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}
		goalConflictMessages.size() == 1
		goalConflictMessages[0].url == "http://www.poker.com"
		goalConflictMessages[0].status == "DISCLOSURE_ACCEPTED"

		//check disclosure response message
		def disclosureResponseMessage = getBobMessagesResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "DisclosureResponseMessage"}[0]
		disclosureResponseMessage.status == "DISCLOSURE_ACCEPTED"
		disclosureResponseMessage.message == responseMessageText
		disclosureResponseMessage.nickname == richard.nickname
		assertEquals(disclosureResponseMessage.creationTime, YonaServer.now)
		disclosureResponseMessage._links.keySet() == ["self", "edit", "related", "yona:buddy", "yona:user", "yona:markRead", "yona:dayDetails"] as Set
		disclosureResponseMessage._links?.related?.href == goalConflictMessages[0]._links.self.href
		disclosureResponseMessage._links?."yona:user"?.href.startsWith(YonaServer.stripQueryString(richard.url))
		disclosureResponseMessage._embedded?."yona:user" == null
		// link to Richard's activity present
		def dayDetailsUrl = disclosureResponseMessage._links."yona:dayDetails"?.href
		dayDetailsUrl
		def dayActivityDetail = appService.getResourceWithPassword(dayDetailsUrl, bob.password)
		assertResponseStatusOk(dayActivityDetail)
		dayActivityDetail.responseData.date == YonaServer.toIsoDateString(YonaServer.now)
		dayActivityDetail.responseData._links."yona:goal".href == goalBuddyRichard.url

		assertMarkReadUnread(bob, disclosureResponseMessage)

		//check delete
		disclosureResponseMessage._links.edit
		def deleteResponse = appService.deleteResourceWithPassword(disclosureResponseMessage._links.edit.href, bob.password)
		assertResponseStatusOk(deleteResponse)
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
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob
		analysisService.postToAnalysisEngine(richard.requestingDevice, ["Gambling"], "http://www.poker.com")
		def getMessagesResponse = appService.getMessages(bob)
		assertResponseStatusOk(getMessagesResponse)
		def disclosureRequestUrl = getMessagesResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "GoalConflictMessage"}[0]._links."yona:requestDisclosure".href
		assertResponseStatusOk(appService.postMessageActionWithPassword(disclosureRequestUrl, [ : ], bob.password))
		getMessagesResponse = appService.getMessages(richard)
		assertResponseStatusOk(getMessagesResponse)
		def disclosureRequestMessage = getMessagesResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "DisclosureRequestMessage"}[0]
		def disclosureRequestRejectUrl = disclosureRequestMessage._links."yona:reject".href

		when:
		def responseMessageText = "Nope!"
		def response = appService.postMessageActionWithPassword(disclosureRequestRejectUrl, [ "message" : responseMessageText ], richard.password)

		then:
		assertResponseStatusOk(response)
		response.responseData._embedded."yona:affectedMessages".size() == 1
		response.responseData._embedded."yona:affectedMessages"[0]._links.self.href == disclosureRequestMessage._links.self.href
		response.responseData._embedded."yona:affectedMessages"[0].status == "DISCLOSURE_REJECTED"
		response.responseData._embedded."yona:affectedMessages"[0]._links."yona:accept" == null
		response.responseData._embedded."yona:affectedMessages"[0]._links."yona:reject" == null

		def getRichardMessagesResponse = appService.getMessages(richard)
		assertResponseStatusOk(getRichardMessagesResponse)
		def disclosureRequestMessages = getRichardMessagesResponse.responseData._embedded."yona:messages".findAll{ it."@type" == "DisclosureRequestMessage"}
		disclosureRequestMessages.size() == 1
		disclosureRequestMessages[0].status == "DISCLOSURE_REJECTED"
		disclosureRequestMessages[0]._links."yona:accept" == null
		disclosureRequestMessages[0]._links."yona:reject" == null
		disclosureRequestMessages[0]._links."yona:dayDetails"?.href

		def getBobMessagesResponse = appService.getMessages(bob)
		assertResponseStatusOk(getBobMessagesResponse)
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
		assertResponseStatusOk(deleteResponse)
		def getBobMessagesResponseAfterDelete = appService.getMessages(bob)
		getBobMessagesResponseAfterDelete.responseData._embedded."yona:messages".findAll{ it."@type" == "DiscloseResponseMessage"}.size() == 0

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}
}
