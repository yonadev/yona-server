package nu.yona.server

import groovy.json.*
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

		cleanup:
			newAppService.deleteUser(john)
	}

	def 'Create John Doe and confirm mobile number'()
	{
		given:
			def ts = timestamp
			def john = createJohnDoe(ts)

		when:
			def response = newAppService.confirmMobileNumber(newAppService.&assertResponseStatusSuccess, john)

		then:
			testUser(john, true, ts)
			// TODO: Verify that confirmMobileNumber action link exists (YD-126)

		cleanup:
			newAppService.deleteUser(john)
	}

	def 'Delete John Doe before confirming the mobile number'()
	{
		given:
			def ts = timestamp
			def john = createJohnDoe(ts)

		when:
			def response = newAppService.deleteUser(john)

		then:
			response.status == 200
			def getUserResponse = newAppService.getUser(john.url, false)
			getUserResponse.status == 400 || getUserResponse.status == 404;
	}

	def 'Hacking attempt: Brute force mobile number confirmation code'()
	{
		given:
			def ts = timestamp
			def john = createJohnDoe(ts)
			def confirmationCode = john.mobileNumberConfirmationCode

		when:
			def response1TimeWrong = newAppService.confirmMobileNumber(john.url, """ { "code":"${confirmationCode}1" } """, john.password)
			newAppService.confirmMobileNumber(john.url, """ { "code":"${confirmationCode}2" } """, john.password)
			newAppService.confirmMobileNumber(john.url, """ { "code":"${confirmationCode}3" } """, john.password)
			newAppService.confirmMobileNumber(john.url, """ { "code":"${confirmationCode}4" } """, john.password)
			def response5TimesWrong = newAppService.confirmMobileNumber(john.url, """ { "code":"${confirmationCode}5" } """, john.password)
			def response6TimesWrong = newAppService.confirmMobileNumber(john.url, """ { "code":"${confirmationCode}6" } """, john.password)
			def response7thTimeRight = newAppService.confirmMobileNumber(john.url, """ { "code":"${confirmationCode}" } """, john.password)

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
			newAppService.deleteUser(john)
	}

	def 'Get John Doe with private data'()
	{
		given:
			def ts = timestamp
			def johnAsCreated = createJohnDoe(ts)

		when:
			def john = newAppService.getUser(newAppService.&assertUserGetResponseDetailsWithPrivateData, johnAsCreated.url, true, johnAsCreated.password)

		then:
			testUser(john, true, ts)

		cleanup:
			newAppService.deleteUser(johnAsCreated)
	}

	def 'Try to get John Doe\'s private data with a bad password'()
	{
		given:
			def ts = timestamp
			def johnAsCreated = createJohnDoe(ts)

		when:
			def response = newAppService.getUser(johnAsCreated.url, true, "nonsense")

		then:
			response.status == 400
			response.responseData.code == "error.decrypting.data"

		cleanup:
			newAppService.deleteUser(johnAsCreated)
	}

	def 'Get John Doe without private data'()
	{
		given:
			def ts = timestamp
			def johnAsCreated = createJohnDoe(ts)

		when:
			def john = newAppService.getUser(newAppService.&assertUserGetResponseDetailsWithoutPrivateData, johnAsCreated.url, false, johnAsCreated.password)

		then:
			testUser(john, false, ts)

		cleanup:
			newAppService.deleteUser(johnAsCreated)
	}

	def 'Update John Doe with the same mobile number'()
	{
		given:
			def ts = timestamp
			def john = createJohnDoe(ts)
			newAppService.confirmMobileNumber(newAppService.&assertResponseStatusSuccess, john)

		when:
			def newNickname = "Johnny"
			def updatedJohn = john.convertToJSON()
			updatedJohn.nickname = newNickname
			def userUpdateResponse = newAppService.updateUser(john.url, updatedJohn, john.password);

		then:
			userUpdateResponse.status == 200
			userUpdateResponse.responseData.mobileNumberConfirmed == true
			userUpdateResponse.responseData.nickname == newNickname

		cleanup:
			newAppService.deleteUser(john)
	}

	def 'Update John Doe with a different mobile number'()
	{
		given:
			def ts = timestamp
			def john = createJohnDoe(ts)
			newAppService.confirmMobileNumber(newAppService.&assertResponseStatusSuccess, john)

		when:
			String newMobileNumber = "${john.mobileNumber}1"
			def updatedJohn = john.convertToJSON()
			updatedJohn.mobileNumber = newMobileNumber
			println "updatedJohn=$updatedJohn"
			def userUpdateResponse = newAppService.updateUser(john.url, updatedJohn, john.password);

		then:
			userUpdateResponse.status == 200
			userUpdateResponse.responseData.mobileNumberConfirmed == false
			userUpdateResponse.responseData.mobileNumber == newMobileNumber

		cleanup:
			newAppService.deleteUser(john)
	}

	User createJohnDoe(def ts)
	{
		newAppService.addUser(newAppService.&assertUserCreationResponseDetails, password, firstName, lastName, nickname,
			"+$ts", devices, goals)
	}

	void testUser(user, includePrivateData, timestamp) {
		assert user.firstName == "John"
		assert user.lastName == "Doe"
		assert user.mobileNumber == "+${timestamp}"
		if (includePrivateData) {
			newAppService.assertUserWithPrivateData(user)
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
		} else {
			assert user.nickname == null
			assert user.devices == null
			assert user.goals == null
		}
	}
}
