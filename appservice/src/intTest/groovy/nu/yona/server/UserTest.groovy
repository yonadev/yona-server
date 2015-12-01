package nu.yona.server

import groovyx.net.http.RESTClient
import groovyx.net.http.HttpResponseException
import spock.lang.Ignore
import spock.lang.IgnoreRest
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll
import groovy.json.*

class UserTest extends Specification {

	def appServiceBaseURL = System.properties.'yona.appservice.url'
	def YonaServer appService = new YonaServer(appServiceBaseURL)
	@Shared
	def timestamp = YonaServer.getTimeStamp()
	def userCreationJSON = """{
				"firstName":"John",
				"lastName":"Doe ${timestamp}",
				"nickname":"JD ${timestamp}",
				"mobileNumber":"+${timestamp}",
				"devices":[
					"Galaxy mini"
				],
				"goals":[
					"gambling"
				]}"""
	def password = "John Doe"

	def 'Create John Doe'(){
		given:

		when:
			def response = appService.addUser(userCreationJSON, password)

		then:
			response.status == 201
			testUser(response.responseData, true)

		cleanup:
			if (response.status == 201) {
				appService.deleteUser(appService.stripQueryString(response.responseData._links.self.href), password)
			}
	}

	def 'Send mobile number confirmation code'(){
		given:
			def userAddResponse = appService.addUser(userCreationJSON, password);
			def userURL = appService.stripQueryString(userAddResponse.responseData._links.self.href);
			def confirmationCode = userAddResponse.responseData.confirmationCode;

		when:
			def response = appService.confirmMobileNumber(userURL, """ { "code":"${confirmationCode}" } """, password)

		then:
			confirmationCode != null
			userAddResponse.status == 201
			userAddResponse.responseData.mobileNumberConfirmed == false
			response.status == 200
			response.responseData.mobileNumberConfirmed == true

		cleanup:
			if (userURL)
			{
				appService.deleteUser(userURL, password)
			}
	}

	def 'Get John Doe with private data'(){
		given:
			def userURL = appService.stripQueryString(appService.addUser(userCreationJSON, password).responseData._links.self.href);

		when:
			def response = appService.getUser(userURL, true, password)

		then:
			response.status == 200
			testUser(response.responseData, true)

		cleanup:
			appService.deleteUser(userURL, password)
	}

	def 'Try to get John Doe\'s private data with a bad password'(){
		given:
			def userURL = appService.stripQueryString(appService.addUser(userCreationJSON, password).responseData._links.self.href);

		when:
			def response = appService.getUser(userURL, true, "nonsense")

		then:
			response.status == 400

		cleanup:
			appService.deleteUser(userURL, password)
	}

	def 'Get John Doe without private data'(){
		given:
			def userURL = appService.stripQueryString(appService.addUser(userCreationJSON, password).responseData._links.self.href);

		when:
			def response = appService.getUser(userURL, false)

		then:
			response.status == 200
			testUser(response.responseData, false)

		cleanup:
			appService.deleteUser(userURL, password)
	}
	
	def 'Update John Doe with the same mobile number'(){
		given:
			def userAddResponse = appService.addUser(userCreationJSON, password);
			def userURL = appService.stripQueryString(userAddResponse.responseData._links.self.href);
			def confirmationCode = userAddResponse.responseData.confirmationCode;
			def confirmResponse = appService.confirmMobileNumber(userURL, """ { "code":"${confirmationCode}" } """, password)
			def newNickname = "Johnny"

		when:
			def userUpdateResponse = appService.updateUser(userURL, userCreationJSON.replace("JD ${timestamp}", newNickname), password);

		then:
			userUpdateResponse.status == 200
			userUpdateResponse.responseData.mobileNumberConfirmed == true
			userUpdateResponse.responseData.nickName == newNickname

		cleanup:
			if (userURL)
			{
				appService.deleteUser(userURL, password)
			}
	}
	
	def 'Update John Doe with a different mobile number'(){
		given:
			def userAddResponse = appService.addUser(userCreationJSON, password);
			def userURL = appService.stripQueryString(userAddResponse.responseData._links.self.href);
			def confirmationCode = userAddResponse.responseData.confirmationCode;
			def confirmResponse = appService.confirmMobileNumber(userURL, """ { "code":"${confirmationCode}" } """, password)
			def newMobileNumber = "+${timestamp}1"

		when:
			def userUpdateResponse = appService.updateUser(userURL, userCreationJSON.replace("+${timestamp}", newMobileNumber), password);
			def newConfirmationCode
			def newConfirmResponse
			if(userUpdateResponse.status == 200)
			{
				newConfirmationCode = userUpdateResponse.responseData.confirmationCode;
				newConfirmResponse = appService.confirmMobileNumber(userURL, """ { "code":"${newConfirmationCode}" } """, password)
			}

		then:
			userUpdateResponse.status == 200
			userUpdateResponse.responseData.mobileNumberConfirmed == false
			userUpdateResponse.responseData.mobileNumber == newMobileNumber
			newConfirmationCode != confirmationCode
			newConfirmResponse.status == 200
			newConfirmResponse.responseData.mobileNumberConfirmed == true

		cleanup:
			if (userURL)
			{
				appService.deleteUser(userURL, password)
			}
	}

	def 'Delete John Doe'(){
		given:
			def userURL = appService.stripQueryString(appService.addUser(userCreationJSON, password).responseData._links.self.href);

		when:
			def response = appService.deleteUser(userURL, password)

		then:
			response.status == 200
			verifyUserDoesNotExist(userURL)
	}

	void testUser(responseData, includePrivateData)
	{
		assert responseData.firstName == "John"
		assert responseData.lastName == "Doe ${timestamp}"
		assert responseData.mobileNumber == "+${timestamp}"
		if (includePrivateData) {
			assert responseData.nickname == "JD ${timestamp}"
			assert responseData.devices.size() == 1
			assert responseData.devices[0] == "Galaxy mini"
			assert responseData.goals.size() == 1
			assert responseData.goals[0] == "gambling"

			assert responseData.vpnProfile.vpnLoginID ==~ /(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/
			assert responseData.vpnProfile.vpnPassword.length() == 32
			assert responseData.vpnProfile.openVPNProfile.length() > 10

			assert responseData._embedded.buddies != null
			assert responseData._embedded.buddies.size() == 0
		} else {
			assert responseData.nickname == null
			assert responseData.devices == null
			assert responseData.goals == null
		}
	}

	void verifyUserDoesNotExist(userURL)
	{
		def response = appService.getUser(userURL, false)
		assert response.status == 400 || response.status == 404;
	}
}
