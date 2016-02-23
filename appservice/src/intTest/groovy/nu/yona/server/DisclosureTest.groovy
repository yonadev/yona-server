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
		def messagesRichard = responseRichard.responseData._embedded.messages.findAll{ it."@type" == "GoalConflictMessage"}
		messagesRichard.size() == 1
		messagesRichard[0].nickname == "<self>"
		messagesRichard[0].activityCategoryName == "news"
		messagesRichard[0].url != null
		messagesRichard[0]._links.requestDisclosure == null

		responseBob.status == 200
		def messagesBob = responseBob.responseData._embedded.messages.findAll{ it."@type" == "GoalConflictMessage"}
		messagesBob.size() == 1
		messagesBob[0].nickname == richard.nickname
		messagesBob[0].activityCategoryName == "news"
		messagesBob[0].url == null
		messagesBob[0]._links.requestDisclosure

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
		def goalConflictMessage = appService.getMessages(bob).responseData._embedded.messages.findAll{ it."@type" == "GoalConflictMessage"}[0]
		def discloseRequestURL = goalConflictMessage._links.requestDisclosure.href

		when:
		def requestMessageText = "Can I have a look?"
		def response = appService.postMessageActionWithPassword(discloseRequestURL, [ "message" : requestMessageText], bob.password)

		then:
		response.status == 200
		response.responseData.properties.status == "done"
		response.responseData._embedded.affectedMessages.size() == 1
		response.responseData._embedded.affectedMessages[0]._links.self.href == goalConflictMessage._links.self.href
		response.responseData._embedded.affectedMessages[0].status == "DISCLOSE_REQUESTED"
		response.responseData._embedded.affectedMessages[0]._links.requestDisclosure == null

		def getRichardMessagesResponse = appService.getMessages(richard)
		getRichardMessagesResponse.status == 200
		def discloseRequestMessages = getRichardMessagesResponse.responseData._embedded.messages.findAll{ it."@type" == "DiscloseRequestMessage"}
		discloseRequestMessages.size() == 1
		discloseRequestMessages[0].status == "DISCLOSE_REQUESTED"
		discloseRequestMessages[0].message == requestMessageText
		discloseRequestMessages[0].targetGoalConflictMessage.activityCategoryName == "gambling"
		discloseRequestMessages[0].targetGoalConflictMessage.creationTime > (System.currentTimeMillis() - 50000) // TODO Use standard date/time format
		discloseRequestMessages[0].user.firstName == "Bob"
		discloseRequestMessages[0]._links.related.href == getRichardMessagesResponse.responseData._embedded.messages.findAll{ it."@type" == "GoalConflictMessage"}[0]._links.self.href
		discloseRequestMessages[0]._links.accept.href
		discloseRequestMessages[0]._links.reject.href

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
		def discloseRequestURL = appService.getMessages(bob).responseData._embedded.messages.findAll{ it."@type" == "GoalConflictMessage"}[0]._links.requestDisclosure.href
		appService.postMessageActionWithPassword(discloseRequestURL, [ : ], bob.password)
		def discloseRequestMessage = appService.getMessages(richard).responseData._embedded.messages.findAll{ it."@type" == "DiscloseRequestMessage"}[0]
		def discloseRequestAcceptURL = discloseRequestMessage._links.accept.href

		when:
		def responseMessageText = "Sure!"
		def response = appService.postMessageActionWithPassword(discloseRequestAcceptURL, [ "message" : responseMessageText ], richard.password)

		then:
		response.status == 200
		response.responseData._embedded.affectedMessages.size() == 1
		response.responseData._embedded.affectedMessages[0]._links.self.href == discloseRequestMessage._links.self.href
		response.responseData._embedded.affectedMessages[0].status == "DISCLOSE_ACCEPTED"
		response.responseData._embedded.affectedMessages[0]._links.accept == null
		response.responseData._embedded.affectedMessages[0]._links.reject == null

		def getRichardMessagesResponse = appService.getMessages(richard)
		getRichardMessagesResponse.status == 200
		def discloseRequestMessages = getRichardMessagesResponse.responseData._embedded.messages.findAll{ it."@type" == "DiscloseRequestMessage"}
		discloseRequestMessages.size() == 1
		discloseRequestMessages[0].status == "DISCLOSE_ACCEPTED"
		discloseRequestMessages[0]._links.accept == null
		discloseRequestMessages[0]._links.reject == null

		def getBobMessagesResponse = appService.getMessages(bob)
		getBobMessagesResponse.status == 200
		def goalConflictMessages = getBobMessagesResponse.responseData._embedded.messages.findAll{ it."@type" == "GoalConflictMessage"}
		goalConflictMessages.size() == 1
		goalConflictMessages[0].url == "http://www.poker.com"
		goalConflictMessages[0].status == "DISCLOSE_ACCEPTED"

		//check disclose response message
		def discloseResponseMessage = getBobMessagesResponse.responseData._embedded.messages.findAll{ it."@type" == "DiscloseResponseMessage"}[0]
		discloseResponseMessage.status == "DISCLOSE_ACCEPTED"
		discloseResponseMessage.message == responseMessageText
		discloseResponseMessage.nickname == richard.nickname
		discloseResponseMessage.user.firstName == "Richard"

		//check delete
		discloseResponseMessage._links.edit
		def deleteResponse = appService.deleteResourceWithPassword(discloseResponseMessage._links.edit.href, bob.password)
		deleteResponse.status == 200
		def getBobMessagesResponseAfterDelete = appService.getMessages(bob)
		!getBobMessagesResponseAfterDelete.responseData._embedded.discloseResponseMessages

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
		def discloseRequestURL = appService.getMessages(bob).responseData._embedded.messages.findAll{ it."@type" == "GoalConflictMessage"}[0]._links.requestDisclosure.href
		appService.postMessageActionWithPassword(discloseRequestURL, [ : ], bob.password)
		def discloseRequestMessage = appService.getMessages(richard).responseData._embedded.messages.findAll{ it."@type" == "DiscloseRequestMessage"}[0]
		def discloseRequestRejectURL = discloseRequestMessage._links.reject.href

		when:
		def responseMessageText = "Nope!"
		def response = appService.postMessageActionWithPassword(discloseRequestRejectURL, [ "message" : responseMessageText ], richard.password)

		then:
		response.status == 200
		response.responseData._embedded.affectedMessages.size() == 1
		response.responseData._embedded.affectedMessages[0]._links.self.href == discloseRequestMessage._links.self.href
		response.responseData._embedded.affectedMessages[0].status == "DISCLOSE_REJECTED"
		response.responseData._embedded.affectedMessages[0]._links.accept == null
		response.responseData._embedded.affectedMessages[0]._links.reject == null

		def getRichardMessagesResponse = appService.getMessages(richard)
		getRichardMessagesResponse.status == 200
		def discloseRequestMessages = getRichardMessagesResponse.responseData._embedded.messages.findAll{ it."@type" == "DiscloseRequestMessage"}
		discloseRequestMessages.size() == 1
		discloseRequestMessages[0].status == "DISCLOSE_REJECTED"
		discloseRequestMessages[0]._links.accept == null
		discloseRequestMessages[0]._links.reject == null

		def getBobMessagesResponse = appService.getMessages(bob)
		getBobMessagesResponse.status == 200
		def goalConflictMessages = getBobMessagesResponse.responseData._embedded.messages.findAll{ it."@type" == "GoalConflictMessage"}
		goalConflictMessages.size() == 1
		goalConflictMessages[0].url == null
		goalConflictMessages[0].status == "DISCLOSE_REJECTED"

		//check disclose response message
		def discloseResponseMessage = getBobMessagesResponse.responseData._embedded.messages.findAll{ it."@type" == "DiscloseResponseMessage"}[0]
		discloseResponseMessage.status == "DISCLOSE_REJECTED"
		discloseResponseMessage.message == responseMessageText
		discloseResponseMessage.nickname == richard.nickname
		discloseResponseMessage.user.firstName == "Richard"

		//check delete
		discloseResponseMessage._links.edit
		def deleteResponse = appService.deleteResourceWithPassword(discloseResponseMessage._links.edit.href, bob.password)
		deleteResponse.status == 200
		def getBobMessagesResponseAfterDelete = appService.getMessages(bob)
		getBobMessagesResponseAfterDelete.responseData._embedded.messages.findAll{ it."@type" == "DiscloseResponseMessage"}.size() == 0

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}
}