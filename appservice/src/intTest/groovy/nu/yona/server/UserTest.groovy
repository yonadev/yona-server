/*******************************************************************************
 * Copyright (c) 2015, 2020 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import static nu.yona.server.test.CommonAssertions.*

import nu.yona.server.test.CommonAssertions
import nu.yona.server.test.User

class UserTest extends AbstractAppServiceIntegrationTest
{
	static final def firstName = "John"
	static final def lastName = "Doe"
	static final def nickname = "JD"

	def 'Create John Doe'()
	{
		given:
		def ts = timestamp

		when:
		def john = createJohnDoe(ts)

		then:
		testUser(john, false, ts)
		def baseUserUrl = YonaServer.stripQueryString(john.url)
		// The below asserts checks returned URLs match the request URL. This is important when going through a proxy
		baseUserUrl.startsWith(appService.url)

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
		def johnAfterNumberConfirmation = appService.confirmMobileNumber(CommonAssertions.&assertUserGetResponseDetails, johnAsCreated)

		then:
		User john = appService.reloadUser(johnAfterNumberConfirmation)
		testUser(john, true, ts)
		john.mobileNumberConfirmationUrl == null

		def baseUserUrl = YonaServer.stripQueryString(john.url)
		// The below asserts check the path fragments. If one of these asserts fails, the Swagger spec needs to be updated too
		john.buddiesUrl == baseUserUrl + "/buddies/"
		YonaServer.stripQueryString(john.goalsUrl) == baseUserUrl + "/goals/"
		john.messagesUrl == baseUserUrl + "/messages/"
		john.newDeviceRequestUrl == appService.url + "/newDeviceRequests/" + john.mobileNumber
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
		assertResponseStatusNoContent(response)
		def getUserResponse = appService.getUser(john.url)
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
		def responseRequestResend = appService.yonaServer.postJson(john.resendMobileNumberConfirmationCodeUrl, "{}", [:], ["Yona-Password" : john.password])

		then:
		assertResponseStatus(response1TimeWrong, 400)
		response1TimeWrong.responseData.code == "error.mobile.number.confirmation.code.mismatch"
		response1TimeWrong.responseData.remainingAttempts == 4

		assertResponseStatusNoContent(responseRequestResend)

		def response1TimeWrongAgain = confirmMobileNumber(john, "12341")
		assertResponseStatus(response1TimeWrongAgain, 400)
		response1TimeWrongAgain.responseData.code == "error.mobile.number.confirmation.code.mismatch"
		response1TimeWrongAgain.responseData.remainingAttempts == 4
		def responseRight = confirmMobileNumber(john, "1234")
		assertResponseStatusOk(responseRight)

		cleanup:
		appService.deleteUser(john)
	}

	def 'Get John Doe'()
	{
		given:
		def ts = timestamp
		def johnAsCreated = createJohnDoe(ts)

		when:
		def john = appService.reloadUser(johnAsCreated)

		then:
		testUser(john, false, ts)

		cleanup:
		appService.deleteUser(johnAsCreated)
	}

	def 'Try to get John Doe\'s data with a bad password'()
	{
		given:
		def ts = timestamp
		def johnAsCreated = createJohnDoe(ts)

		when:
		def response = appService.getUser(johnAsCreated.url, "nonsense")

		then:
		assertResponseStatus(response, 400)
		response.responseData.code == "error.decrypting.data"

		cleanup:
		appService.deleteUser(johnAsCreated)
	}

	def 'Try to get John Doe \'without private data\''()
	{
		given:
		def ts = timestamp
		User johnAsCreated = createJohnDoe(ts)

		when:
		def response = appService.getUser(YonaServer.stripQueryString(johnAsCreated.url), johnAsCreated.password)

		then:
		assertResponseStatus(response, 400)
		response.responseData.message ==~ /.*'requestingUserId' is not present.*/

		cleanup:
		appService.deleteUser(johnAsCreated)
	}

	def 'Try to get John Doe without Yona password header'()
	{
		given:
		def ts = timestamp
		User johnAsCreated = createJohnDoe(ts)

		when:
		def response = appService.getUser(johnAsCreated.url)

		then:
		assertResponseStatus(response, 400)
		response.responseData.code == "error.missing.password.header"

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
		response.responseData.code == "error.user.exists"

		cleanup:
		appService.deleteUser(john)
	}

	def 'Try update John Doe without requesting device ID request parameter'()
	{
		given:
		def ts = timestamp
		User richard = addRichard()
		def john = createJohnDoe(ts)
		appService.confirmMobileNumber(CommonAssertions.&assertResponseStatusSuccess, john)

		when:
		def updatedJohn = john.convertToJson()
		updatedJohn.mobileNumber = richard.mobileNumber
		def response = appService.updateUser(YonaServer.removeRequestParam(john.url, "requestingDeviceId"), updatedJohn, john.password)

		then:
		assertResponseStatus(response, 400)
		response.responseData.code == "error.request.missing.request.parameter"
		response.data.message ==~ /^Request parameter 'requestingDeviceId' is mandatory in this context.*/

		cleanup:
		appService.deleteUser(john)
	}

	def 'Try update John Doe with device data'()
	{
		given:
		def ts = timestamp
		def john = createJohnDoe(ts)
		appService.confirmMobileNumber(CommonAssertions.&assertResponseStatusSuccess, john)

		when:
		def updatedJohn = john.convertToJson()
		updatedJohn.deviceName = "My phone"
		updatedJohn.deviceOperatingSystem = "ANDROID"
		updatedJohn.deviceAppVersion = "3.0"
		updatedJohn.deviceAppVersionCode = 10000
		updatedJohn.deviceFirebaseInstanceId = "SomeLongString"
		def response = appService.updateUser(john.url, updatedJohn, john.password)

		then:
		assertResponseStatus(response, 400)
		response.responseData.code == "error.request.extra.property"
		response.data.message ==~ /^Property 'deviceName' is not supported in this context.*/

		cleanup:
		appService.deleteUser(john)
	}

	def 'Try get user with invalid ID'()
	{
		given:
		User richard = addRichard()

		when:
		def response = appService.getUser(richard.url.replaceFirst(/requestingUserId=..../, "requestingUserId=QQQQ"))

		then:
		assertResponseStatus(response, 400)
		assert response.responseData.code == "error.invalid.uuid"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Try get nonexisting user'()
	{
		given:
		User richard = addRichard()

		when:
		def url = richard.url.replaceFirst(/users\/........-/, "users/00000000-")
		url = url.replaceFirst(/requestingUserId=........-/, "requestingUserId=00000000-")
		def response = appService.getResourceWithPassword(url, richard.password)

		then:
		assertResponseStatus(response, 400)
		assert response.responseData.code == "error.user.not.found.id"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Try delete nonexisting user'()
	{
		given:
		User richard = addRichard()

		when:
		def url = richard.editUrl.replaceFirst(/users\/........-/, "users/00000000-")
		def response = appService.deleteResourceWithPassword(url, richard.password)

		then:
		assertResponseStatus(response, 400)
		assert response.responseData.code == "error.user.not.found.id"

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
		reportNetworkActivity(richard.requestingDevice, ["YouTube"], "http://www.youtube.com", relativeActivityDate)
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
		reportNetworkActivity(richard.requestingDevice, ["YouTube"], "http://www.youtube.com", "W-1 Thu 15:00")
		reportAppActivity(richard, richard.requestingDevice, "NU.nl", "W-1 Fri 23:55", relativeActivityDate)
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
		"some name" | "IOS" | "1.1" | 5000 | 201
		"some name" | null | null | null | 400
		"some name" | "IOS" | null | null | 400
		"some name" | null | "1.1" | null | 400
		"some name" | null | null | 5000 | 400
		"some name" | null | "1.1" | 50 | 400
		"some name" | "IOS" | "1.1" | null | 400
		"some name" | "IOS" | null | 5000 | 400
		null | "IOS" | "1.1" | 5000 | 400
		null | null | null | null | 201
		null | "IOS" | null | null | 400
		null | null | "1.1" | null | 400
		null | null | null | 5000 | 400
		null | null | "1.1" | 5000 | 400
		null | "IOS" | "1.1" | null | 400
		null | "IOS" | null | 5000 | 400
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

	private void testUser(User user, mobileNumberConfirmed, timestamp)
	{
		assert user.mobileNumber == makeMobileNumber(timestamp)
		assertEquals(user.creationTime, YonaServer.now)
		assertEquals(user.appLastOpenedDate, YonaServer.now.toLocalDate())

		assertUser(user)
		assert user.firstName == "John"
		assert user.lastName == "Doe"
		assert user.nickname == "JD"

		assert user.buddies != null
		assert user.buddies.size() == 0
		if (mobileNumberConfirmed)
		{
			assert user.goals.size() == 1 // Mandatory goal added
			assert user.goals[0].activityCategoryUrl == GAMBLING_ACT_CAT_URL
			assert user.devices.size() == 1 // Default device
			assert user.requestingDevice.name == "First device"
		}
		else
		{
			assert user.goals == null
			assert user.devices == null
		}
	}
}
