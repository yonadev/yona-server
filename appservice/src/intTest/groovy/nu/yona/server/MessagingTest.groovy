package nu.yona.server

import groovy.json.*
import spock.lang.Shared

class MessagingTest extends AbstractAppServiceIntegrationTest
{
	def 'Richard pages through his messages'()
	{
		given:
			def richardAndBob = addRichardAndBobAsBuddies()
			def richard = richardAndBob["richard"]
			def bob = richardAndBob["bob"]
			newAnalysisService.postToAnalysisEngine(richard.vpnProfile.vpnLoginID, ["news/media"], "http://www.refdag.nl")
			newAnalysisService.postToAnalysisEngine(bob.vpnProfile.vpnLoginID, ["Gambling"], "http://www.poker'com")
			newAnalysisService.postToAnalysisEngine(bob.vpnProfile.vpnLoginID, ["news/media"], "http://www.refdag.nl")

		when:
			def allMessagesResponse = newAppService.getAnonymousMessages(richard)
			def firstPageMessagesResponse = newAppService.getAnonymousMessages(richard, [
				"page": 0,
				"size": 2,
				"sort": "creationTime"])
			def secondPageMessagesResponse = newAppService.getAnonymousMessages(richard, [
				"page": 1,
				"size": 2,
				"sort": "creationTime"])

		then:
			allMessagesResponse.status == 200
			allMessagesResponse.responseData._links.self.href == richard.url + newAppService.ANONYMOUS_MESSAGES_PATH_FRAGMENT + "{?page,size,sort}"
			allMessagesResponse.responseData._embedded.buddyConnectResponseMessages
			allMessagesResponse.responseData._embedded.buddyConnectResponseMessages.size() == 1
			allMessagesResponse.responseData._embedded.goalConflictMessages
			allMessagesResponse.responseData._embedded.goalConflictMessages.size() == 3

			firstPageMessagesResponse.status == 200
			firstPageMessagesResponse.responseData._links.self.href == richard.url + newAppService.ANONYMOUS_MESSAGES_PATH_FRAGMENT + "?page=0&size=2&sort=creationTime"
			!firstPageMessagesResponse.responseData._links.prev
			firstPageMessagesResponse.responseData._links.next
			!firstPageMessagesResponse.responseData._embedded.buddyConnectResponseMessages
			firstPageMessagesResponse.responseData._embedded.goalConflictMessages
			firstPageMessagesResponse.responseData._embedded.goalConflictMessages.size() == 2
			firstPageMessagesResponse.responseData.page.totalElements == 4

			secondPageMessagesResponse.status == 200
			secondPageMessagesResponse.responseData._links.self.href == richard.url + newAppService.ANONYMOUS_MESSAGES_PATH_FRAGMENT + "?page=1&size=2&sort=creationTime"
			secondPageMessagesResponse.responseData._links.prev
			!secondPageMessagesResponse.responseData._links.next
			secondPageMessagesResponse.responseData._embedded.buddyConnectResponseMessages
			secondPageMessagesResponse.responseData._embedded.buddyConnectResponseMessages.size() == 1
			secondPageMessagesResponse.responseData._embedded.goalConflictMessages
			secondPageMessagesResponse.responseData._embedded.goalConflictMessages.size() == 1
			secondPageMessagesResponse.responseData.page.totalElements == 4
	}
}
