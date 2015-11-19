package nu.yona.server

import groovyx.net.http.RESTClient
import spock.lang.Ignore
import spock.lang.IgnoreRest
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll
import groovy.json.*

class CreateUserOnBuddyRequestTest extends Specification {

	def adminServiceBaseURL = System.properties.'yona.adminservice.url'
	def YonaServer adminService = new YonaServer(adminServiceBaseURL)

	def analysisServiceBaseURL = System.properties.'yona.analysisservice.url'
	def YonaServer analysisService = new YonaServer(analysisServiceBaseURL)

	def appServiceBaseURL = System.properties.'yona.appservice.url'
	def YonaServer appService = new YonaServer(appServiceBaseURL)
	@Shared
	def timestamp = YonaServer.getTimeStamp()

	@Shared
	def richardQuinPassword = "R i c h a r d"
	def bobDunnPassword = "B o b"
	@Shared
	def richardQuinURL
	@Shared
	def richardQuinLoginID
	@Shared
	def bobDunnGmail = "bobdunn325@gmail.com"
	@Shared
	def bobDunnGmailPassword = "bobbydunn"
	@Shared
	def bobDunnInviteURL
	@Shared
	def bobDunnURL
	@Shared
	def bobDunnLoginID
	@Shared
	def richardQuinBobBuddyURL
	@Shared
	def bobDunnRichardBuddyURL
	@Shared
	def bobDunnBuddyMessageAcceptURL
	@Shared
	def bobDunnBuddyMessageProcessURL
	@Shared
	def richardQuinBuddyMessageAcceptURL
	@Shared
	def richardQuinBuddyMessageProcessURL 

	def 'Add user Richard Quin'(){
		given:

		when:
			def response = appService.addUser("""{
				"firstName":"Richard ${timestamp}",
				"lastName":"Quin ${timestamp}",
				"nickName":"RQ ${timestamp}",
				"mobileNumber":"+${timestamp}1",
				"devices":[
					"Nexus 6"
				],
				"goals":[
					"news"
				]
			}""", richardQuinPassword)
			richardQuinURL = appService.stripQueryString(response.responseData._links.self.href)
			richardQuinLoginID = response.responseData.vpnProfile.loginID;

		then:
			response.status == 201
			richardQuinURL.startsWith(appServiceBaseURL + appService.USERS_PATH)

		cleanup:
			println "URL Richard: " + richardQuinURL
	}

	def 'Richard requests Bob to become his buddy'(){
		given:

		when:
			def response = appService.requestBuddy(richardQuinURL, """{
				"_embedded":{
					"user":{
						"firstName":"Bob ${timestamp}",
						"lastName":"Dun ${timestamp}n",
						"emailAddress":"${bobDunnGmail}",
						"mobileNumber":"+ ${timestamp}2"
					}
				},
				"message":"Would you like to be my buddy?"
			}""", richardQuinPassword)
			richardQuinBobBuddyURL = response.responseData._links.self.href
			
			def bobDunnInviteEmail = getLastMessageFromGmail(bobDunnGmail, bobDunnGmailPassword)
			def bobDunnInviteURLMatch = bobDunnInviteEmail =~ /$appServiceBaseURL[^" ]+/
			assert bobDunnInviteURLMatch.size() == 1
			bobDunnInviteURL = bobDunnInviteURLMatch[0]

		then:
			response.status == 201
			response.responseData._embedded.user.firstName == "Bob ${timestamp}"
			richardQuinBobBuddyURL.startsWith(richardQuinURL)

		cleanup:
			println "URL buddy Richard: " + richardQuinBobBuddyURL

	}
	
	def 'Bob Dunn downloads the app and opens the link sent in the email with the app; app retrieves data to prefill'(){
		given:

		when:
			def response = appService.getUser(bobDunnInviteURL, true, null)

		then:
			response.status == 200
	}
	
	def 'Bob Dunn adjusts data and submits; app saves with new password'(){
		given:

		when:
			def response = appService.updateUser(bobDunnInviteURL, """{
				"firstName":"Bob ${timestamp}",
				"lastName":"Dunn ${timestamp}",
				"nickName":"BD ${timestamp}",
				"mobileNumber":"+${timestamp}2",
				"devices":[
					"iPhone 6"
				],
				"goals":[
					"gambling"
				]
			}""", bobDunnPassword)

		then:
			response.status == 200
	}
	
	def 'Delete users'(){
		given:

		when:
			def responseRichard = appService.deleteUser(richardQuinURL, richardQuinPassword)
			def responseBob = appService.deleteUser(bobDunnURL, bobDunnPassword)

		then:
			responseRichard.status == 200
			responseBob.status == 200
	}
}