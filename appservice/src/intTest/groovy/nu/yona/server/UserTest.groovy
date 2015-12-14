package nu.yona.server

import groovy.json.*
import nu.yona.server.YonaServer
import nu.yona.server.test.User

class UserTest extends AbstractAppServiceIntegrationTest
{
	final def firstName = "John"
	final def lastName = "Doe"
	final def nickname = "JD"
	final def devices = [ "Galaxy mini" ]
	final def goals = [ "gambling" ]
	def password = "J o h n   D o e"

	def 'Create John Doe'()
	{
		given:
			def ts = timestamp

		when:
			def john = createJohnDoe(ts)

		then:
			testUser(john, true, ts)
			john.confirmMobileNumberUrl == YonaServer.stripQueryString(john.url) + appService.MOBILE_NUMBER_CONFIRMATION_PATH_FRAGMENT


		cleanup:
			appService.deleteUser(john)
	}

	def 'Create John Doe and confirm mobile number'()
	{
		given:
			def ts = timestamp
			def john = createJohnDoe(ts)

		when:
			def response = appService.confirmMobileNumber(appService.&assertResponseStatusSuccess, john)

		then:
			testUser(john, true, ts)
			// john.confirmMobileNumberUrl == null YD-126: This URL shouldn't be available anymore when the number is confirmed

		cleanup:
			appService.deleteUser(john)
	}

	def 'Delete John Doe before confirming the mobile number'()
	{
		given:
			def ts = timestamp
			def john = createJohnDoe(ts)

		when:
			def response = appService.deleteUser(john)

		then:
			response.status == 200
			def getUserResponse = appService.getUser(john.url, false)
			getUserResponse.status == 400 || getUserResponse.status == 404;
	}

	def 'Hacking attempt: Brute force mobile number confirmation code'()
	{
		given:
			def ts = timestamp
			def john = createJohnDoe(ts)
			def confirmationCode = john.mobileNumberConfirmationCode

		when:
			def response1TimeWrong = appService.confirmMobileNumber(john.url, """{ "code":"${confirmationCode}1" } """, john.password)
			appService.confirmMobileNumber(john.url, """{ "code":"${confirmationCode}2" } """, john.password)
			appService.confirmMobileNumber(john.url, """{ "code":"${confirmationCode}3" } """, john.password)
			appService.confirmMobileNumber(john.url, """{ "code":"${confirmationCode}4" } """, john.password)
			def response5TimesWrong = appService.confirmMobileNumber(john.url, """{ "code":"${confirmationCode}5" } """, john.password)
			def response6TimesWrong = appService.confirmMobileNumber(john.url, """{ "code":"${confirmationCode}6" } """, john.password)
			def response7thTimeRight = appService.confirmMobileNumber(john.url, """{ "code":"${confirmationCode}" } """, john.password)

		then:
			response1TimeWrong.status == 400
			response1TimeWrong.responseData.code == "error.mobile.number.confirmation.code.mismatch"
			response5TimesWrong.status == 400
			response5TimesWrong.responseData.code == "error.mobile.number.confirmation.code.mismatch"
			response6TimesWrong.status == 400
			response6TimesWrong.responseData.code == "error.too.many.wrong.attempts"
			response7thTimeRight.status == 400
			response7thTimeRight.responseData.code == "error.too.many.wrong.attempts"

		cleanup:
			appService.deleteUser(john)
	}

	def 'Get John Doe with private data'()
	{
		given:
			def ts = timestamp
			def johnAsCreated = createJohnDoe(ts)

		when:
			def john = appService.getUser(appService.&assertUserGetResponseDetailsWithPrivateData, johnAsCreated.url, true, johnAsCreated.password)

		then:
			testUser(john, true, ts)

		cleanup:
			appService.deleteUser(johnAsCreated)
	}

	def 'Try to get John Doe\'s private data with a bad password'()
	{
		given:
			def ts = timestamp
			def johnAsCreated = createJohnDoe(ts)

		when:
			def response = appService.getUser(johnAsCreated.url, true, "nonsense")

		then:
			response.status == 400
			response.responseData.code == "error.decrypting.data"

		cleanup:
			appService.deleteUser(johnAsCreated)
	}

	def 'Get John Doe without private data'()
	{
		given:
			def ts = timestamp
			def johnAsCreated = createJohnDoe(ts)

		when:
			def john = appService.getUser(appService.&assertUserGetResponseDetailsWithoutPrivateData, johnAsCreated.url, false, johnAsCreated.password)

		then:
			testUser(john, false, ts)

		cleanup:
			appService.deleteUser(johnAsCreated)
	}

	def 'Update John Doe with the same mobile number'()
	{
		given:
			def ts = timestamp
			def john = createJohnDoe(ts)
			appService.confirmMobileNumber(appService.&assertResponseStatusSuccess, john)

		when:
			def newNickname = "Johnny"
			def updatedJohn = john.convertToJSON()
			updatedJohn.nickname = newNickname
			def userUpdateResponse = appService.updateUser(john.url, updatedJohn, john.password);

		then:
			userUpdateResponse.status == 200
			userUpdateResponse.responseData._links?.confirmMobileNumber?.href == null
			userUpdateResponse.responseData.nickname == newNickname

		cleanup:
			appService.deleteUser(john)
	}

	def 'Update John Doe with a different mobile number'()
	{
		given:
			def ts = timestamp
			def john = createJohnDoe(ts)
			appService.confirmMobileNumber(appService.&assertResponseStatusSuccess, john)

		when:
			String newMobileNumber = "${john.mobileNumber}1"
			def updatedJohn = john.convertToJSON()
			updatedJohn.mobileNumber = newMobileNumber
			def userUpdateResponse = appService.updateUser(john.url, updatedJohn, john.password);

		then:
			userUpdateResponse.status == 200
			userUpdateResponse.responseData._links?.confirmMobileNumber?.href != null
			userUpdateResponse.responseData.mobileNumber == newMobileNumber

		cleanup:
			appService.deleteUser(john)
	}

	User createJohnDoe(def ts)
	{
		appService.addUser(appService.&assertUserCreationResponseDetails, password, firstName, lastName, nickname,
			"+$ts", devices, goals)
	}

	void testUser(user, includePrivateData, timestamp)
	{
		assert user.firstName == "John"
		assert user.lastName == "Doe"
		assert user.mobileNumber == "+${timestamp}"
		if (includePrivateData)
		{
			appService.assertUserWithPrivateData(user)
			assert user.nickname == "JD"
			assert user.devices.size() == 1
			assert user.devices[0] == "Galaxy mini"
			assert user.goals.size() == 1
			assert user.goals[0] == "gambling"

			assert user.vpnProfile.vpnLoginID ==~ /(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/
			assert user.vpnProfile.vpnPassword.length() == 32
			assert user.vpnProfile.openVPNProfile.length() > 10

			assert user.buddies != null
			assert user.buddies.size() == 0
		}
		else
		{
			assert user.nickname == null
			assert user.devices == null
			assert user.goals == null
		}
	}
}
