/*******************************************************************************
 * Copyright (c) 2015, 2018 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import static nu.yona.server.test.CommonAssertions.*

import groovy.json.*
import nu.yona.server.test.CommonAssertions
import nu.yona.server.test.User

class UserTest extends AbstractAppServiceIntegrationTest
{
	final def firstName = "John"
	final def lastName = "Doe"
	final def nickname = "JD"

	def 'Create John Doe'()
	{
		given:
		def ts = timestamp

		when:
		def john = createJohnDoe(ts)

		then:
		testUser(john, true, false, ts)
		def baseUserUrl = YonaServer.stripQueryString(john.url)
		// The below assert checks the path fragment. If it fails, the Swagger spec needs to be updated too
		john.mobileNumberConfirmationUrl == baseUserUrl + "/confirmMobileNumber?requestingDeviceId=" + john.getRequestingDeviceId()
		john.resendMobileNumberConfirmationCodeUrl == baseUserUrl + "/resendMobileNumberConfirmationCode"

		def getMessagesResponse = appService.yonaServer.getResourceWithPassword(baseUserUrl + "/messages/", john.password)
		assertResponseStatus(getMessagesResponse, 400)
		getMessagesResponse.responseData.code == "error.mobile.number.not.confirmed"

		cleanup:
		appService.deleteUser(john)
	}

	def 'Create John Doe and confirm mobile number'()
	{
		given:
		def ts = timestamp
		def johnAsCreated = createJohnDoe(ts)

		when:
		def johnAfterNumberConfirmation = appService.confirmMobileNumber(CommonAssertions.&assertUserGetResponseDetailsWithPrivateData, johnAsCreated)

		then:
		User john = appService.reloadUser(johnAfterNumberConfirmation)
		testUser(john, true, true, ts)
		john.mobileNumberConfirmationUrl == null

		def baseUserUrl = YonaServer.stripQueryString(john.url)
		// The below asserts check the path fragments. If one of these asserts fails, the Swagger spec needs to be updated too
		john.postOpenAppEventUrl == baseUserUrl + "/devices/" + john.getRequestingDeviceId() + "/openApp"
		john.buddiesUrl == baseUserUrl + "/buddies/"
		john.goalsUrl.startsWith(baseUserUrl + "/goals/")
		john.messagesUrl == baseUserUrl + "/messages/"
		john.newDeviceRequestUrl == appService.url + "/newDeviceRequests/" + john.mobileNumber
		john.appActivityUrl == baseUserUrl + "/devices/" + john.getRequestingDeviceId() + "/appActivity/"
		john.appleMobileConfig == baseUserUrl + "/devices/" + john.getRequestingDeviceId() + "/apple.mobileconfig"
		john.pinResetRequestUrl == baseUserUrl + "/pinResetRequest/request"
		john.dailyActivityReportsUrl == baseUserUrl + "/activity/days/"
		john.dailyActivityReportsWithBuddiesUrl == baseUserUrl + "/activity/withBuddies/days/?requestingDeviceId=" + john.getRequestingDeviceId()
		john.weeklyActivityReportsUrl == baseUserUrl + "/activity/weeks/"

		cleanup:
		appService.deleteUser(johnAfterNumberConfirmation)
	}

	def 'Delete John Doe before confirming the mobile number'()
	{
		given:
		def ts = timestamp
		def john = createJohnDoe(ts)

		when:
		def response = appService.deleteUser(john)

		then:
		assertResponseStatusOk(response)
		def getUserResponse = appService.getUser(john.url, false)
		getUserResponse.status == 400 || getUserResponse.status == 404
	}

	def 'Hacking attempt: Brute force mobile number confirmation code'()
	{
		given:
		def ts = timestamp
		def john = createJohnDoe(ts)

		when:
		def response1TimeWrong = confirmMobileNumber(john, "12341")
		confirmMobileNumber(john, "12342")
		confirmMobileNumber(john, "12343")
		def response4TimesWrong = confirmMobileNumber(john, "12344")
		def response5TimesWrong = confirmMobileNumber(john, "12345")
		def response6TimesWrong = confirmMobileNumber(john, "12346")
		def response7thTimeRight = confirmMobileNumber(john, "1234")

		then:
		assertResponseStatus(response1TimeWrong, 400)
		assertResponseStatus(response1TimeWrong, 400)
		response1TimeWrong.responseData.code == "error.mobile.number.confirmation.code.mismatch"
		response1TimeWrong.responseData.remainingAttempts == 4
		assertResponseStatus(response4TimesWrong, 400)
		response4TimesWrong.responseData.code == "error.mobile.number.confirmation.code.mismatch"
		response4TimesWrong.responseData.remainingAttempts == 1
		assertResponseStatus(response5TimesWrong, 400)
		response5TimesWrong.responseData.code == "error.mobile.number.confirmation.code.mismatch"
		response5TimesWrong.responseData.remainingAttempts == 0
		assertResponseStatus(response6TimesWrong, 400)
		response6TimesWrong.responseData.code == "error.mobile.number.confirmation.code.too.many.failed.attempts"
		response6TimesWrong.responseData.remainingAttempts == null
		assertResponseStatus(response7thTimeRight, 400)
		response7thTimeRight.responseData.code == "error.mobile.number.confirmation.code.too.many.failed.attempts"

		cleanup:
		appService.deleteUser(john)
	}

	def 'Request resend of confirmation code'()
	{
		given:
		def ts = timestamp
		User john = createJohnDoe(ts)
		def response1TimeWrong = confirmMobileNumber(john, "12341")

		when:
		def responseRequestResend = appService.yonaServer.postJson(john.resendMobileNumberConfirmationCodeUrl, "{}", ["Yona-Password" : john.password])

		then:
		assertResponseStatus(response1TimeWrong, 400)
		response1TimeWrong.responseData.code == "error.mobile.number.confirmation.code.mismatch"
		response1TimeWrong.responseData.remainingAttempts == 4

		assertResponseStatusOk(responseRequestResend)

		def response1TimeWrongAgain = confirmMobileNumber(john, "12341")
		assertResponseStatus(response1TimeWrongAgain, 400)
		response1TimeWrongAgain.responseData.code == "error.mobile.number.confirmation.code.mismatch"
		response1TimeWrongAgain.responseData.remainingAttempts == 4
		def responseRight = confirmMobileNumber(john, "1234")
		assertResponseStatusOk(responseRight)

		cleanup:
		appService.deleteUser(john)
	}

	def 'Get John Doe with private data'()
	{
		given:
		def ts = timestamp
		def johnAsCreated = createJohnDoe(ts)

		when:
		def john = appService.reloadUser(johnAsCreated)

		then:
		testUser(john, true, false, ts)

		cleanup:
		appService.deleteUser(johnAsCreated)
	}

	def 'Get John Doe with private data using legacy includePrivateData param'()
	{
		given:
		def ts = timestamp
		User johnAsCreated = createJohnDoe(ts)

		when:
		def response = appService.yonaServer.getResourceWithPassword(YonaServer.stripQueryString(johnAsCreated.url), johnAsCreated.password, ["includePrivateData": "true"])

		then:
		assertResponseStatusOk(response)
		testUser(new User(response.responseData), true, false, ts)

		cleanup:
		appService.deleteUser(johnAsCreated)
	}

	def 'Get John Doe without private data using legacy includePrivateData param'()
	{
		given:
		def ts = timestamp
		User johnAsCreated = createJohnDoe(ts)

		when:
		def response = appService.yonaServer.getResourceWithPassword(YonaServer.stripQueryString(johnAsCreated.url), johnAsCreated.password, ["includePrivateData": "false"])

		then:
		assertResponseStatusOk(response)
		testUser(new User(response.responseData), false, false, ts)

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
		assertResponseStatus(response, 400)
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
		def john = appService.getUser(CommonAssertions.&assertUserGetResponseDetailsWithoutPrivateData, YonaServer.stripQueryString(johnAsCreated.url))

		then:
		testUser(john, false, false, ts)

		cleanup:
		appService.deleteUser(johnAsCreated)
	}

	def 'Update John Doe with the same mobile number'()
	{
		given:
		def ts = timestamp
		def john = createJohnDoe(ts)
		appService.confirmMobileNumber(CommonAssertions.&assertResponseStatusSuccess, john)

		when:
		def newNickname = "Johnny"
		def updatedJohn = john.convertToJson()
		updatedJohn.nickname = newNickname
		def userUpdateResponse = appService.updateUser(john.url, updatedJohn, john.password)

		then:
		assertResponseStatusOk(userUpdateResponse)
		userUpdateResponse.responseData._links?."yona:confirmMobileNumber"?.href == null
		userUpdateResponse.responseData.nickname == newNickname
		assertDateTimeFormat(userUpdateResponse.responseData.creationTime)

		cleanup:
		appService.deleteUser(john)
	}

	def 'Update John Doe with a different mobile number'()
	{
		given:
		def ts = timestamp
		def john = createJohnDoe(ts)
		appService.confirmMobileNumber(CommonAssertions.&assertResponseStatusSuccess, john)

		when:
		String newMobileNumber = "${john.mobileNumber}1"
		def updatedJohn = john.convertToJson()
		updatedJohn.mobileNumber = newMobileNumber
		def userUpdateResponse = appService.updateUser(john.url, updatedJohn, john.password)

		then:
		assertResponseStatusOk(userUpdateResponse)
		userUpdateResponse.responseData._links?."yona:confirmMobileNumber"?.href != null
		userUpdateResponse.responseData.mobileNumber == newMobileNumber
		assertDateTimeFormat(userUpdateResponse.responseData.creationTime)

		cleanup:
		appService.deleteUser(john)
	}

	def 'Try update John Doe with an existing mobile number'()
	{
		given:
		def ts = timestamp
		User richard = addRichard()
		def john = createJohnDoe(ts)
		appService.confirmMobileNumber(CommonAssertions.&assertResponseStatusSuccess, john)

		when:
		def updatedJohn = john.convertToJson()
		updatedJohn.mobileNumber = richard.mobileNumber
		def response = appService.updateUser(john.url, updatedJohn, john.password)

		then:
		assertResponseStatus(response, 400)
		assert response.responseData.code == "error.user.exists"

		cleanup:
		appService.deleteUser(john)
	}

	def 'Try get user with invalid ID'()
	{
		given:
		User richard = addRichard()

		when:
		def response = appService.getUser(richard.url.replaceFirst(/requestingUserId=..../, "requestingUserId=QQQQ"), false)

		then:
		assertResponseStatus(response, 400)
		assert response.responseData.code == "error.invalid.uuid"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Retrieve OVPN profile and SSL root certificate (YD-541, YD-544)'()
	{
		given:
		User richard = addRichard()

		when:
		assert richard.vpnProfile.ovpnProfileUrl
		assert richard.sslRootCertUrl
		def responseOvpnProfile = appService.yonaServer.restClient.get(path: richard.vpnProfile.ovpnProfileUrl)
		def responseSslRootCert = appService.yonaServer.restClient.get(path: richard.sslRootCertUrl)

		then:
		assertResponseStatusOk(responseOvpnProfile)
		responseOvpnProfile.contentType == "application/x-openvpn-profile"
		assertResponseStatusOk(responseSslRootCert)
		responseSslRootCert.contentType == "application/pkix-cert"
		richard.sslRootCertCn == "smoothwall003.yona"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Retrieve appleMobileConfig (YD-544)'()
	{
		given:
		User richard = addRichard()

		when:
		assert richard.appleMobileConfig
		def responseAppleMobileConfig = appService.yonaServer.restClient.get(path: richard.appleMobileConfig, headers: ["Yona-Password":richard.password])

		then:
		assertResponseStatusOk(responseAppleMobileConfig)
		responseAppleMobileConfig.contentType == "application/x-apple-aspen-config"
		def appleMobileConfig = responseAppleMobileConfig.responseData.text
		appleMobileConfig.contains("<string>${richard.vpnProfile.vpnLoginId}\\n${richard.vpnProfile.vpnPassword}</string>")

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Retrieve SSL root certificate'()
	{
		given:
		User richard = addRichard()

		when:
		assert richard.sslRootCertUrl
		def responseSslRootCertUrl = appService.yonaServer.restClient.get(path: richard.sslRootCertUrl, headers: ["Yona-Password":richard.password])

		then:
		assertResponseStatusOk(responseSslRootCertUrl)
		responseSslRootCertUrl.contentType == "application/pkix-cert"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Retrieve Apple App site association'()
	{
		when:
		def responseAppleAppSiteAssociation = appService.yonaServer.restClient.get(path: "/.well-known/apple-app-site-association")

		then:
		assertResponseStatusOk(responseAppleAppSiteAssociation)
		responseAppleAppSiteAssociation.contentType == "application/json"
		responseAppleAppSiteAssociation.responseData.applinks.details[0].appID ==~ /.*\.yona/
	}

	def 'Last monitored activity date is not present when there were no activities'()
	{
		given:
		User richard = addRichard()

		when:
		richard = appService.reloadUser(richard)

		then:
		richard.lastMonitoredActivityDate == null

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Last monitored activity date is updated properly after one activity'()
	{
		given:
		def richard = addRichard()

		when:
		def relativeActivityDate = "W-1 Thu 15:00"
		reportNetworkActivity(richard, ["YouTube"], "http://www.youtube.com", relativeActivityDate)
		richard = appService.reloadUser(richard)

		then:
		richard.lastMonitoredActivityDate == YonaServer.relativeDateTimeStringToZonedDateTime(relativeActivityDate).toLocalDate()

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Last monitored activity date is updated properly after multiple activities'()
	{
		given:
		def richard = addRichard()

		when:
		def relativeActivityDate = "W-1 Sat 00:10"
		reportNetworkActivity(richard, ["YouTube"], "http://www.youtube.com", "W-1 Thu 15:00")
		reportAppActivity(richard, "NU.nl", "W-1 Fri 23:55", relativeActivityDate)
		richard = appService.reloadUser(richard)

		then:
		richard.lastMonitoredActivityDate == YonaServer.relativeDateTimeStringToZonedDateTime(relativeActivityDate).toLocalDate()

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Try to post incorrect device information'(deviceName, operatingSystem, appVersion, appVersionCode, responseStatus)
	{
		given:
		def ts = timestamp

		when:
		def jsonStr = User.makeUserJsonStringWithDeviceInfo(firstName, lastName, nickname, makeMobileNumber(ts), deviceName, operatingSystem, appVersion, appVersionCode)
		def response = appService.addUser(jsonStr)

		then:
		assertResponseStatus(response, responseStatus)

		where:
		deviceName | operatingSystem | appVersion | appVersionCode | responseStatus
		"some name" | "IOS" | "1.1" | 50 | 201
		"some name" | null | null | null | 400
		"some name" | "IOS" | null | null | 400
		"some name" | null | "1.1" | null | 400
		"some name" | null | null | 50 | 400
		"some name" | null | "1.1" | 50 | 400
		"some name" | "IOS" | "1.1" | null | 400
		"some name" | "IOS" | null | 50 | 400
		null | "IOS" | "1.1" | 50 | 400
		null | null | null | null | 201
		null | "IOS" | null | null | 400
		null | null | "1.1" | null | 400
		null | null | null | 50 | 400
		null | null | "1.1" | 50 | 400
		null | "IOS" | "1.1" | null | 400
		null | "IOS" | null | 50 | 400
	}

	private def confirmMobileNumber(User user, code)
	{
		appService.confirmMobileNumber(user.mobileNumberConfirmationUrl, """{ "code":"${code}" } """, user.password)
	}

	private User createJohnDoe(ts)
	{
		appService.addUser(CommonAssertions.&assertUserCreationResponseDetails, firstName, lastName, nickname,
				makeMobileNumber(ts))
	}

	private void testUser(User user, includePrivateData, mobileNumberConfirmed, timestamp)
	{
		assert user.mobileNumber == makeMobileNumber(timestamp)
		assertEquals(user.creationTime, YonaServer.now)
		assertEquals(user.appLastOpenedDate, YonaServer.now.toLocalDate())

		if (includePrivateData)
		{
			assertUserWithPrivateData(user)
			assert user.firstName == "John"
			assert user.lastName == "Doe"
			assert user.nickname == "JD"

			assert user.buddies != null
			assert user.buddies.size() == 0
			if (mobileNumberConfirmed)
			{
				assert user.vpnProfile.vpnLoginId ==~ /$CommonAssertions.VPN_LOGIN_ID_PATTERN/
				assert user.vpnProfile.vpnPassword.length() == 32
				assert user.vpnProfile.ovpnProfileUrl

				assert user.goals.size() == 1 // Mandatory goal added
				assert user.goals[0].activityCategoryUrl == GAMBLING_ACT_CAT_URL
				assert user.devices.size() == 1 // Default device
				assert user.devices[0].name == "First device"
			}
			else
			{
				assert user.goals == null
				assert user.devices == null
			}
		}
		else
		{
			assert user.firstName == null
			assert user.lastName == null
			assert user.nickname == null
			assert user.goals == null
		}
	}
}
