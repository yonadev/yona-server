package nu.yona.server

import groovy.json.*
import spock.lang.Shared
import spock.lang.Specification

class OverwriteUserTest extends Specification {

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
	@Shared
	def richardQuinNewPassword = "R i c h a r d 1"
	@Shared
	def richardQuinURL
	@Shared
	def richardQuinVPNLoginID
	@Shared
	def richardQuinMobileNumberConfirmationCode
	@Shared
	def richardQuinOverwriteConfirmationCode

	def 'Add user Richard Quin'(){
		given:

		when:
		def response = appService.addUser("""{
					"firstName":"Richard ${timestamp}",
					"lastName":"Quin ${timestamp}",
					"nickname":"RQ ${timestamp}",
					"mobileNumber":"+${timestamp}1",
					"devices":[
						"Nexus 6"
					],
					"goals":[
						"news"
					]
				}""", richardQuinPassword)
		if (response.status == 201) {
			richardQuinURL = appService.stripQueryString(response.responseData._links.self.href)
			richardQuinVPNLoginID = response.responseData.vpnProfile.vpnLoginID;
			richardQuinMobileNumberConfirmationCode = response.responseData.confirmationCode;
		}

		then:
		response.status == 201
		richardQuinURL.startsWith(appServiceBaseURL + appService.USERS_PATH)
		richardQuinMobileNumberConfirmationCode != null

		cleanup:
		println "URL Richard: " + richardQuinURL
	}

	def 'Confirm Richard\'s mobile number'(){
		when:
		def response = appService.confirmMobileNumber(richardQuinURL, """ { "code":"${richardQuinMobileNumberConfirmationCode}" } """, richardQuinPassword)

		then:
		response.status == 200
		response.responseData.mobileNumberConfirmed == true
	}

	def 'Attempt to add another user with the same mobile number'(){
		when:
		def response = appService.addUser("""{
					"firstName":"Richardo ${timestamp}",
					"lastName":"Quino ${timestamp}",
					"nickname":"RQo ${timestamp}",
					"mobileNumber":"+${timestamp}1",
					"devices":[
						"Nexus 6"
					],
					"goals":[
						"news"
					]
				}""", "Foo")

		then:
		response.status == 400
		response.responseData.code == "error.user.exists"
	}

	def 'Request overwrite of the existing user'(){
		when:
		def response = appService.updateResource(appService.USERS_PATH, """ { } """, [:], ["mobileNumber":"+${timestamp}1"])
		if(response.status == 200) {
			richardQuinOverwriteConfirmationCode = response.responseData.confirmationCode
		}
		then:
		response.status == 200
		richardQuinOverwriteConfirmationCode
	}

	def 'Overwrite the existing user'(){
		when:
		def response = appService.addUser("""{
					"firstName":"Richard ${timestamp}",
					"lastName":"Quin ${timestamp}",
					"nickname":"RQ ${timestamp}",
					"mobileNumber":"+${timestamp}1",
					"devices":[
						"Nexus 6"
					],
					"goals":[
						"news"
					]
				}""", richardQuinNewPassword, ["overwriteUserConfirmationCode": richardQuinOverwriteConfirmationCode])

		then:
		response.status == 201
		response.responseData.mobileNumberConfirmed == true
	}

	def 'Delete user Richard'(){
		when:
		def response = appService.deleteUser(richardQuinURL, richardQuinPassword)

		then:
		response.status == 200
	}
}
