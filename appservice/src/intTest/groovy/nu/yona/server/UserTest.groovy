/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import groovy.json.*
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
		// The below assert checks the path fragment. If it fails, the Swagger spec needs to be updated too
		john.mobileNumberConfirmationUrl == john.url + "/confirmMobileNumber"
		john.resendMobileNumberConfirmationCodeUrl == john.url + "/resendMobileNumberConfirmationCode"

		def getMessagesResponse = appService.yonaServer.getResourceWithPassword(john.url + "/messages/", john.password)
		getMessagesResponse.status == 400
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
		def johnAfterNumberConfirmation = appService.confirmMobileNumber(appService.&assertUserGetResponseDetailsWithPrivateData, johnAsCreated)

		then:
		User john = appService.reloadUser(johnAfterNumberConfirmation)
		testUser(john, true, true, ts)
		john.mobileNumberConfirmationUrl == null

		// The below asserts check the path fragments. If one of these asserts fails, the Swagger spec needs to be updated too
		john.postOpenAppEventUrl == john.url + "/openApp"
		john.buddiesUrl == john.url + "/buddies/"
		john.goalsUrl == john.url + "/goals/"
		john.messagesUrl == john.url + "/messages/"
		john.newDeviceRequestUrl == appService.url + "/newDeviceRequests/" + john.mobileNumber
		john.appActivityUrl == john.url + "/appActivity/"
		john.pinResetRequestUrl == john.url + "/pinResetRequest/request"
		john.dailyActivityReportsUrl == john.url + "/activity/days/"
		john.dailyActivityReportsWithBuddiesUrl == john.url + "/activity/withBuddies/days/"
		john.weeklyActivityReportsUrl == john.url + "/activity/weeks/"

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
		appService.assertResponseStatus(response, 200)
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
		appService.assertResponseStatus(response1TimeWrong, 400)
		response1TimeWrong.status == 400
		response1TimeWrong.responseData.code == "error.mobile.number.confirmation.code.mismatch"
		response1TimeWrong.responseData.remainingAttempts == 4
		appService.assertResponseStatus(response4TimesWrong, 400)
		response4TimesWrong.responseData.code == "error.mobile.number.confirmation.code.mismatch"
		response4TimesWrong.responseData.remainingAttempts == 1
		appService.assertResponseStatus(response5TimesWrong, 400)
		response5TimesWrong.responseData.code == "error.mobile.number.confirmation.code.mismatch"
		response5TimesWrong.responseData.remainingAttempts == 0
		appService.assertResponseStatus(response6TimesWrong, 400)
		response6TimesWrong.responseData.code == "error.mobile.number.confirmation.code.too.many.failed.attempts"
		response6TimesWrong.responseData.remainingAttempts == null
		appService.assertResponseStatus(response7thTimeRight, 400)
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
		appService.assertResponseStatus(response1TimeWrong, 400)
		response1TimeWrong.responseData.code == "error.mobile.number.confirmation.code.mismatch"
		response1TimeWrong.responseData.remainingAttempts == 4

		appService.assertResponseStatus(responseRequestResend, 200)

		def response1TimeWrongAgain = confirmMobileNumber(john, "12341")
		appService.assertResponseStatus(response1TimeWrongAgain, 400)
		response1TimeWrongAgain.responseData.code == "error.mobile.number.confirmation.code.mismatch"
		response1TimeWrongAgain.responseData.remainingAttempts == 4
		def responseRight = confirmMobileNumber(john, "1234")
		appService.assertResponseStatus(responseRight, 200)

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
		appService.assertResponseStatus(response, 200)
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
		appService.assertResponseStatus(response, 200)
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
		appService.assertResponseStatus(response, 400)
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
		testUser(john, false, false, ts)

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
		def updatedJohn = john.convertToJson()
		updatedJohn.nickname = newNickname
		def userUpdateResponse = appService.updateUser(john.url, updatedJohn, john.password)

		then:
		appService.assertResponseStatus(userUpdateResponse, 200)
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
		appService.confirmMobileNumber(appService.&assertResponseStatusSuccess, john)

		when:
		String newMobileNumber = "${john.mobileNumber}1"
		def updatedJohn = john.convertToJson()
		updatedJohn.mobileNumber = newMobileNumber
		def userUpdateResponse = appService.updateUser(john.url, updatedJohn, john.password)

		then:
		appService.assertResponseStatus(userUpdateResponse, 200)
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
		appService.confirmMobileNumber(appService.&assertResponseStatusSuccess, john)

		when:
		def updatedJohn = john.convertToJson()
		updatedJohn.mobileNumber = richard.mobileNumber
		def response = appService.updateUser(john.url, updatedJohn, john.password)

		then:
		appService.assertResponseStatus(response, 400)
		assert response.responseData.code == "error.user.exists"

		cleanup:
		appService.deleteUser(john)
	}

	def 'Retrieve OVPN profile and SSL root certificate'()
	{
		given:
		User richard = addRichard()

		when:
		assert richard.vpnProfile.ovpnProfileUrl
		assert richard.sslRootCertUrl
		def responseOvpnProfile = appService.yonaServer.restClient.get(path: richard.vpnProfile.ovpnProfileUrl)
		def responseSslRootCert = appService.yonaServer.restClient.get(path: richard.sslRootCertUrl)

		then:
		appService.assertResponseStatus(responseOvpnProfile, 200)
		responseOvpnProfile.contentType == "application/x-openvpn-profile"
		appService.assertResponseStatus(responseSslRootCert, 200)
		responseSslRootCert.contentType == "application/pkix-cert"
		richard.sslRootCertCn == "smoothwall003.yona"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Retrieve appleMobileConfig'()
	{
		given:
		User richard = addRichard()

		when:
		assert richard.appleMobileConfig
		def responseAppleMobileConfig = appService.yonaServer.restClient.get(path: richard.appleMobileConfig, headers: ["Yona-Password":richard.password])

		then:
		appService.assertResponseStatus(responseAppleMobileConfig, 200)
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
		appService.assertResponseStatus(responseSslRootCertUrl, 200)
		responseSslRootCertUrl.contentType == "application/pkix-cert"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Retrieve Apple App site association'()
	{
		when:
		def responseAppleAppSiteAssociation = appService.yonaServer.restClient.get(path: "/.well-known/apple-app-site-association")

		then:
		appService.assertResponseStatus(responseAppleAppSiteAssociation, 200)
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

	private def confirmMobileNumber(User user, code)
	{
		appService.confirmMobileNumber(user.mobileNumberConfirmationUrl, """{ "code":"${code}" } """, user.password)
	}

	private User createJohnDoe(def ts)
	{
		appService.addUser(appService.&assertUserCreationResponseDetails, firstName, lastName, nickname,
				makeMobileNumber(ts))
	}

	private void testUser(User user, includePrivateData, mobileNumberConfirmed, timestamp)
	{
		assert user.firstName == "John"
		assert user.lastName == "Doe"
		assert user.mobileNumber == makeMobileNumber(timestamp)
		assertEquals(user.creationTime, YonaServer.now)
		assertEquals(user.appLastOpenedDate, YonaServer.now.toLocalDate())

		if (includePrivateData)
		{
			appService.assertUserWithPrivateData(user)
			assert user.nickname == "JD"

			assert user.buddies != null
			assert user.buddies.size() == 0
			assert user.goals != null
			if (mobileNumberConfirmed)
			{
				assert user.vpnProfile.vpnLoginId ==~ /(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/
				assert user.vpnProfile.vpnPassword.length() == 32
				assert user.vpnProfile.ovpnProfileUrl

				assert user.goals.size() == 1 //mandatory goal added
				assert user.goals[0].activityCategoryUrl == GAMBLING_ACT_CAT_URL
			}
			else
			{
				assert user.goals.size() == 0
			}
		}
		else
		{
			assert user.nickname == null
			assert user.goals == null
		}
	}
}