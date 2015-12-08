package nu.yona.server

import groovy.json.*
import spock.lang.Shared

class RejectBuddyTest extends AbstractAppServiceIntegrationTest
{
	def 'Bob rejects Richard\'s buddy request"'(){
		given:
			def richard = addRichard();
			def bob = addBob();
			newAppService.sendBuddyConnectRequest(richard, bob)
			def rejectURL = newAppService.fetchBuddyConnectRequestMessage(bob).rejectURL

		when:
			def rejectMessage = "Sorry, not you"
			def rejectResponse = newAppService.postMessageActionWithPassword(rejectURL, ["message" : rejectMessage], bob.password)

		then:
			rejectResponse.status == 200

			// Verify connect message doesn't have actions anymore
			def actionURLs = newAppService.fetchBuddyConnectRequestMessage(bob).rejectURL
			!actionURLs?.size

			def processResult = newAppService.fetchBuddyConnectResponseMessage(richard)
			processResult.message == rejectMessage

			// Have the requesting user process the buddy connect response
			def processResponse = newAppService.postMessageActionWithPassword(processResult.processURL, [ : ], richard.password)
			processResponse.status == 200

			// Verify that Bob is not in Richard's buddy list anymore
			def getRichardBudddiesResponse = newAppService.getBuddies(richard);
			!getRichardBudddiesResponse.responseData?._embedded?.buddies

			// Verify that Richard is not in Bob's buddy list anymore
			def getBobBudddiesResponse = newAppService.getBuddies(bob);
			!getBobBudddiesResponse.responseData?._embedded?.buddies

		cleanup:
			newAppService.deleteUser(richard)
			newAppService.deleteUser(bob)
	}
}
