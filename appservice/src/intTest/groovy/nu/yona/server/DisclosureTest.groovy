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
			newAnalysisService.postToAnalysisEngine(richard, ["news/media"], "http://www.refdag.nl")

		when:
			def responseRichard = newAppService.getAnonymousMessages(richard)
			def responseBob = newAppService.getAnonymousMessages(bob)

		then:
			responseRichard.status == 200
			responseRichard.responseData?._embedded?.goalConflictMessages
			def messagesRichard = responseRichard.responseData._embedded.goalConflictMessages
			messagesRichard.size() == 1
			messagesRichard[0].nickname == "<self>"
			messagesRichard[0].goalName == "news"
			messagesRichard[0].url != null
			messagesRichard[0]._links.requestDisclosure == null

			responseBob.status == 200
			responseBob.responseData?._embedded?.goalConflictMessages
			def messagesBob = responseBob.responseData._embedded.goalConflictMessages
			messagesBob.size() == 1
			messagesBob[0].nickname == richard.nickname
			messagesBob[0].goalName == "news"
			messagesBob[0].url == null
			messagesBob[0]._links.requestDisclosure

		cleanup:
			newAppService.deleteUser(richard)
			newAppService.deleteUser(bob)
	}

	def 'Richard receives Bob\'s request for disclosure of a message'()
	{
		given:
			def richardAndBob = addRichardAndBobAsBuddies()
			def richard = richardAndBob.richard
			def bob = richardAndBob.bob
			newAnalysisService.postToAnalysisEngine(richard, ["Gambling"], "http://www.poker.com")
			def discloseRequestURL = newAppService.getAnonymousMessages(bob).responseData._embedded.goalConflictMessages[0]._links.requestDisclosure.href

		when:
			def response = newAppService.postMessageActionWithPassword(discloseRequestURL, [ : ], bob.password)

		then:
			response.status == 200
			def getRichardAnonMessagesResponse = newAppService.getAnonymousMessages(richard)
			getRichardAnonMessagesResponse.status == 200
			getRichardAnonMessagesResponse.responseData?._embedded?.discloseRequestMessages
			def discloseRequestMessages = getRichardAnonMessagesResponse.responseData._embedded.discloseRequestMessages
			discloseRequestMessages.size() == 1
			discloseRequestMessages[0].targetGoalConflictMessage.goalName == "gambling"
			discloseRequestMessages[0].targetGoalConflictMessage.creationTime > (System.currentTimeMillis() - 50000) // TODO Use standard date/time format
			discloseRequestMessages[0]._links.related.href == getRichardAnonMessagesResponse.responseData._embedded.goalConflictMessages[0]._links.self.href
			discloseRequestMessages[0]._links.accept.href
			discloseRequestMessages[0]._links.reject.href

		cleanup:
			newAppService.deleteUser(richard)
			newAppService.deleteUser(bob)
	}

	def 'Richard accepts Bob\'s request for disclosure of a message'()
	{
		given:
			def richardAndBob = addRichardAndBobAsBuddies()
			def richard = richardAndBob.richard
			def bob = richardAndBob.bob
			newAnalysisService.postToAnalysisEngine(richard, ["Gambling"], "http://www.poker.com")
			def discloseRequestURL = newAppService.getAnonymousMessages(bob).responseData._embedded.goalConflictMessages[0]._links.requestDisclosure.href
			newAppService.postMessageActionWithPassword(discloseRequestURL, [ : ], bob.password)
			def discloseRequestAcceptURL = newAppService.getAnonymousMessages(richard).responseData._embedded.discloseRequestMessages[0]._links.accept.href

		when:
			def response = newAppService.postMessageActionWithPassword(discloseRequestAcceptURL, [ : ], richard.password)

		then:
			response.status == 200
			def getRichardAnonMessagesResponse = newAppService.getAnonymousMessages(richard)
			getRichardAnonMessagesResponse.status == 200
			getRichardAnonMessagesResponse.responseData?._embedded?.discloseRequestMessages
			def discloseRequestMessages = getRichardAnonMessagesResponse.responseData._embedded.discloseRequestMessages
			discloseRequestMessages.size() == 1
			discloseRequestMessages[0]._links.accept == null
			discloseRequestMessages[0]._links.reject == null

			def getBobAnonMessagesResponse = newAppService.getAnonymousMessages(bob)
			getBobAnonMessagesResponse.status == 200
			getBobAnonMessagesResponse.responseData?._embedded?.goalConflictMessages
			def goalConflictMessages = getBobAnonMessagesResponse.responseData._embedded.goalConflictMessages
			goalConflictMessages[0].url == "http://www.poker.com"
			goalConflictMessages[0].status == "DISCLOSE_ACCEPTED"

		cleanup:
			newAppService.deleteUser(richard)
			newAppService.deleteUser(bob)
	}

	def 'Richard rejects Bob\'s request for disclosure of a message'()
	{
		given:
			def richardAndBob = addRichardAndBobAsBuddies()
			def richard = richardAndBob.richard
			def bob = richardAndBob.bob
			newAnalysisService.postToAnalysisEngine(richard, ["Gambling"], "http://www.poker.com")
			def discloseRequestURL = newAppService.getAnonymousMessages(bob).responseData._embedded.goalConflictMessages[0]._links.requestDisclosure.href
			newAppService.postMessageActionWithPassword(discloseRequestURL, [ : ], bob.password)
			def discloseRequestRejectURL = newAppService.getAnonymousMessages(richard).responseData._embedded.discloseRequestMessages[0]._links.reject.href

		when:
			def response = newAppService.postMessageActionWithPassword(discloseRequestRejectURL, [ : ], richard.password)

		then:
			response.status == 200
			def getRichardAnonMessagesResponse = newAppService.getAnonymousMessages(richard)
			getRichardAnonMessagesResponse.status == 200
			getRichardAnonMessagesResponse.responseData?._embedded?.discloseRequestMessages
			def discloseRequestMessages = getRichardAnonMessagesResponse.responseData._embedded.discloseRequestMessages
			discloseRequestMessages.size() == 1
			discloseRequestMessages[0]._links.accept == null
			discloseRequestMessages[0]._links.reject == null

			def getBobAnonMessagesResponse = newAppService.getAnonymousMessages(bob)
			getBobAnonMessagesResponse.status == 200
			getBobAnonMessagesResponse.responseData?._embedded?.goalConflictMessages
			def goalConflictMessages = getBobAnonMessagesResponse.responseData._embedded.goalConflictMessages
			goalConflictMessages[0].url == null
			goalConflictMessages[0].status == "DISCLOSE_REJECTED"

		cleanup:
			newAppService.deleteUser(richard)
			newAppService.deleteUser(bob)
	}
}
