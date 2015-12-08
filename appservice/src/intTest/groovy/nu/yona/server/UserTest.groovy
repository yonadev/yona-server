package nu.yona.server

import groovy.json.*
import nu.yona.server.test.AbstractYonaIntegrationTest
import spock.lang.Shared

class UserTest extends AbstractAppServiceIntegrationTest {

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
		if (userURL) {
			appService.deleteUser(userURL, password)
		}
	}

	def 'Hacking attempt: Brute force mobile number confirmation code'(){
		given:
		def userAddResponse = appService.addUser(userCreationJSON, password);
		def userURL = appService.stripQueryString(userAddResponse.responseData._links.self.href);
		def confirmationCode = userAddResponse.responseData.confirmationCode;

		when:
		def response1TimeWrong = appService.confirmMobileNumber(userURL, """ { "code":"${confirmationCode}1" } """, password)
		appService.confirmMobileNumber(userURL, """ { "code":"${confirmationCode}2" } """, password)
		appService.confirmMobileNumber(userURL, """ { "code":"${confirmationCode}3" } """, password)
		appService.confirmMobileNumber(userURL, """ { "code":"${confirmationCode}4" } """, password)
		def response5TimesWrong = appService.confirmMobileNumber(userURL, """ { "code":"${confirmationCode}5" } """, password)
		def response6TimesWrong = appService.confirmMobileNumber(userURL, """ { "code":"${confirmationCode}6" } """, password)
		def response7thTimeRight = appService.confirmMobileNumber(userURL, """ { "code":"${confirmationCode}" } """, password)

		then:
		confirmationCode != null
		userAddResponse.status == 201
		userAddResponse.responseData.mobileNumberConfirmed == false
		response1TimeWrong.status == 400
		response1TimeWrong.responseData.code == "error.mobile.number.confirmation.code.mismatch"
		response5TimesWrong.status == 400
		response5TimesWrong.responseData.code == "error.mobile.number.confirmation.code.mismatch"
		response6TimesWrong.status == 400
		response6TimesWrong.responseData.code == "error.too.many.wrong.attempts"
		response7thTimeRight.status == 400
		response7thTimeRight.responseData.code == "error.too.many.wrong.attempts"

		cleanup:
		if (userURL) {
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
		userUpdateResponse.responseData.nickname == newNickname

		cleanup:
		if (userURL) {
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
		if(userUpdateResponse.status == 200) {
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
		if (userURL) {
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

	void testUser(responseData, includePrivateData) {
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

	void verifyUserDoesNotExist(userURL) {
		def response = appService.getUser(userURL, false)
		assert response.status == 400 || response.status == 404;
	}
}
