/*******************************************************************************
 * Copyright (c) 2015, 2022 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import static nu.yona.server.test.CommonAssertions.assertResponseStatus
import static nu.yona.server.test.CommonAssertions.assertResponseStatusNoContent
import static nu.yona.server.test.CommonAssertions.assertResponseStatusOk

import java.time.Duration

import nu.yona.server.test.CommonAssertions
import nu.yona.server.test.User

class PinResetRequestTest extends AbstractAppServiceIntegrationTest
{
	def setupSpec()
	{
		enableConcurrentRequests(5)
	}

	def 'Cannot verify or clear pin reset request before requesting a pin reset'()
	{
		given:
		User richard = addRichard()

		when:
		User richardAfterGet = appService.reloadUser(richard)

		then:
		richardAfterGet.pinResetRequestUrl
		!richardAfterGet.verifyPinResetUrl
		!richardAfterGet.clearPinResetUrl
		!richardAfterGet.resendPinResetConfirmationCodeUrl

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Request pin reset'()
	{
		given:
		User richard = addRichard()

		when:
		def response = appService.yonaServer.postJson(richard.pinResetRequestUrl, [:], [:], ["Yona-Password": richard.password, "Accept-Language": "nl-NL"])

		then:
		assertResponseStatusOk(response)
		response.json.delay == "PT10S"
		User richardAfterGet = appService.reloadUser(richard, CommonAssertions.&assertUserGetResponseDetailsPinResetRequestedNotGenerated)
		!richardAfterGet.pinResetRequestUrl

		!richardAfterGet.verifyPinResetUrl
		!richardAfterGet.clearPinResetUrl
		!richardAfterGet.resendPinResetConfirmationCodeUrl

		sleepTillPinResetCodeIsGenerated(richard, response.json.delay)
		User richardAfterDelayedGet = appService.reloadUser(richard, CommonAssertions.&assertUserGetResponseDetailsPinResetRequestedAndGenerated)
		// The below asserts check the path fragments. If one of these asserts fails, the Swagger spec needs to be updated too
		richardAfterDelayedGet.verifyPinResetUrl == YonaServer.stripQueryString(richard.url) + "/pinResetRequest/verify"
		richardAfterDelayedGet.clearPinResetUrl == YonaServer.stripQueryString(richard.url) + "/pinResetRequest/clear"
		richardAfterDelayedGet.resendPinResetConfirmationCodeUrl == YonaServer.stripQueryString(richard.url) + "/pinResetRequest/resend"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Request pin reset twice'()
	{
		given:
		User richard = addRichard()
		def firstResponse = appService.yonaServer.postJson(richard.pinResetRequestUrl, [:], [:], ["Yona-Password": richard.password, "Accept-Language": "nl-NL"])
		assertResponseStatusOk(firstResponse)
		sleepTillPinResetCodeIsGenerated(richard, firstResponse.json.delay)
		richard = appService.reloadUser(richard, CommonAssertions.&assertUserGetResponseDetailsPinResetRequestedAndGenerated)
		assert richard.clearPinResetUrl == YonaServer.stripQueryString(richard.url) + "/pinResetRequest/clear"
		assertResponseStatusNoContent(appService.yonaServer.postJson(richard.clearPinResetUrl, [:], [:], ["Yona-Password": richard.password]))
		richard = appService.reloadUser(richard)
		assert richard.pinResetRequestUrl != null

		when:
		def response = appService.yonaServer.postJson(richard.pinResetRequestUrl, [:], [:], ["Yona-Password": richard.password, "Accept-Language": "nl-NL"])

		then:
		assertResponseStatusOk(response)
		response.json.delay == "PT10S"
		User richardAfterGet = appService.reloadUser(richard, CommonAssertions.&assertUserGetResponseDetailsPinResetRequestedNotGenerated)
		!richardAfterGet.pinResetRequestUrl

		!richardAfterGet.verifyPinResetUrl
		!richardAfterGet.clearPinResetUrl
		!richardAfterGet.resendPinResetConfirmationCodeUrl

		sleepTillPinResetCodeIsGenerated(richard, response.json.delay)
		User richardAfterDelayedGet = appService.reloadUser(richard, CommonAssertions.&assertUserGetResponseDetailsPinResetRequestedAndGenerated)
		// The below asserts check the path fragments. If one of these asserts fails, the Swagger spec needs to be updated too
		richardAfterDelayedGet.verifyPinResetUrl == YonaServer.stripQueryString(richard.url) + "/pinResetRequest/verify"
		richardAfterDelayedGet.clearPinResetUrl == YonaServer.stripQueryString(richard.url) + "/pinResetRequest/clear"
		richardAfterDelayedGet.resendPinResetConfirmationCodeUrl == YonaServer.stripQueryString(richard.url) + "/pinResetRequest/resend"

		cleanup:
		appService.deleteUser(richard)
	}

	private static void sleepTillPinResetCodeIsGenerated(User user, def delayString)
	{
		long millis = Duration.parse(delayString).toMillis() + 2000
		// Add 2 seconds margin, to be sure it's completed
		println("$YonaServer.now: sleepTillPinResetCodeIsGenerated: delayString=$delayString, user.url=$user.url, millis: $millis. Entering sleep")
		sleep(millis)
		println("$YonaServer.now: sleepTillPinResetCodeIsGenerated: sleep completed")
	}

	def 'Verify pin reset confirmation code'()
	{
		given:
		User richard = addRichard()
		def resetRequestResponse = appService.yonaServer.postJson(richard.pinResetRequestUrl, [:], [:], ["Yona-Password": richard.password])
		sleepTillPinResetCodeIsGenerated(richard, resetRequestResponse.json.delay)
		richard = appService.reloadUser(richard, CommonAssertions.&assertUserGetResponseDetailsPinResetRequestedAndGenerated)

		when:
		def response = appService.yonaServer.postJson(richard.verifyPinResetUrl, """{"code" : "1234"}""", [:], ["Yona-Password": richard.password])

		then:
		assertResponseStatusNoContent(response)
		User richardAfterGet = appService.reloadUser(richard, CommonAssertions.&assertUserGetResponseDetailsPinResetRequestedAndGenerated)
		!richardAfterGet.pinResetRequestUrl
		richardAfterGet.verifyPinResetUrl
		richardAfterGet.clearPinResetUrl
		richardAfterGet.resendPinResetConfirmationCodeUrl

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Request resend of pin reset confirmation code'()
	{
		given:
		User richard = addRichard()
		def resetRequestResponse = appService.yonaServer.postJson(richard.pinResetRequestUrl, [:], [:], ["Yona-Password": richard.password])
		sleepTillPinResetCodeIsGenerated(richard, resetRequestResponse.json.delay)
		richard = appService.reloadUser(richard, CommonAssertions.&assertUserGetResponseDetailsPinResetRequestedAndGenerated)

		when:
		def response = appService.yonaServer.postJson(richard.resendPinResetConfirmationCodeUrl, """{}""", [:], ["Yona-Password": richard.password])

		then:
		assertResponseStatusNoContent(response)
		User richardAfterGet = appService.reloadUser(richard, CommonAssertions.&assertUserGetResponseDetailsPinResetRequestedAndGenerated)
		!richardAfterGet.pinResetRequestUrl
		richardAfterGet.verifyPinResetUrl
		richardAfterGet.clearPinResetUrl
		richardAfterGet.resendPinResetConfirmationCodeUrl

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Hacking attempt: Try to verify pin reset confirmation code before end of delay period'()
	{
		given:
		User richard = addRichard()
		def responsePost = appService.yonaServer.postJson(richard.pinResetRequestUrl, [:], [:], ["Yona-Password": richard.password])
		sleepTillMidOfPinResetCodeGenerationInterval(richard, responsePost.json.delay)

		when:
		def response = appService.yonaServer.postJson(YonaServer.stripQueryString(richard.url) + "/pinResetRequest/verify", """{"code" : "1234"}""", [:], ["Yona-Password": richard.password])

		then:
		assertResponseStatus(response, 400)
		response.json.code == "error.pin.reset.request.confirmation.code.mismatch"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Hacking attempt: Try to request pin reset confirmation code resend without requesting a pin reset confirmation code'()
	{
		given:
		User richard = addRichard()

		when:
		def response = appService.yonaServer.postJson(YonaServer.stripQueryString(richard.url) + "/pinResetRequest/resend", """{}""", [:], ["Yona-Password": richard.password])

		then:
		assertResponseStatus(response, 400)
		response.json.code == "error.pin.reset.request.confirmation.code.not.set"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Hacking attempt: Try to request pin reset confirmation code resend before end of delay period'()
	{
		given:
		User richard = addRichard()
		def responsePost = appService.yonaServer.postJson(richard.pinResetRequestUrl, [:], [:], ["Yona-Password": richard.password])
		sleepTillMidOfPinResetCodeGenerationInterval(richard, responsePost.json.delay)

		when:
		def response = appService.yonaServer.postJson(YonaServer.stripQueryString(richard.url) + "/pinResetRequest/resend", """{}""", [:], ["Yona-Password": richard.password])

		then:
		assertResponseStatus(response, 400)
		response.json.code == "error.pin.reset.request.confirmation.code.not.set"

		cleanup:
		appService.deleteUser(richard)
	}

	private static void sleepTillMidOfPinResetCodeGenerationInterval(User user, def delayString)
	{
		long millis = Duration.parse(delayString).toMillis() / 2
		println("$YonaServer.now: sleepTillMidOfPinResetCodeGenerationInterval: delayString=$delayString, user.url=$user.url, millis: $millis. Entering sleep")
		sleep(millis)
		println("$YonaServer.now: sleepTillMidOfPinResetCodeGenerationInterval: sleep completed")
	}

	def 'Clear pin reset request before verification'()
	{
		given:
		User richard = addRichard()
		def resetRequestResponse = appService.yonaServer.postJson(richard.pinResetRequestUrl, [:], [:], ["Yona-Password": richard.password])
		sleepTillPinResetCodeIsGenerated(richard, resetRequestResponse.json.delay)
		richard = appService.reloadUser(richard, CommonAssertions.&assertUserGetResponseDetailsPinResetRequestedAndGenerated)

		when:
		def response = appService.yonaServer.postJson(richard.clearPinResetUrl, [:], [:], ["Yona-Password": richard.password])

		then:
		assertResponseStatusNoContent(response)
		User richardAfterGet = appService.reloadUser(richard)
		richardAfterGet.pinResetRequestUrl
		!richardAfterGet.verifyPinResetUrl
		!richardAfterGet.clearPinResetUrl
		!richardAfterGet.resendPinResetConfirmationCodeUrl
		def verifyPinResetAttemptResponse = appService.yonaServer.postJson(richard.verifyPinResetUrl, """{"code" : "1234"}""", [:], ["Yona-Password": richard.password])
		assertResponseStatus(verifyPinResetAttemptResponse, 400)
		verifyPinResetAttemptResponse.json.code == "error.pin.reset.request.confirmation.code.not.set"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Clear pin reset request after verification'()
	{
		given:
		User richard = addRichard()
		def resetRequestResponse = appService.yonaServer.postJson(richard.pinResetRequestUrl, [:], [:], ["Yona-Password": richard.password])
		sleepTillPinResetCodeIsGenerated(richard, resetRequestResponse.json.delay)
		richard = appService.reloadUser(richard, CommonAssertions.&assertUserGetResponseDetailsPinResetRequestedAndGenerated)
		appService.yonaServer.postJson(richard.verifyPinResetUrl, """{"code" : "1234"}""", [:], ["Yona-Password": richard.password])
		richard = appService.reloadUser(richard, CommonAssertions.&assertUserGetResponseDetailsPinResetRequestedAndGenerated)

		when:
		def response = appService.yonaServer.postJson(richard.clearPinResetUrl, [:], [:], ["Yona-Password": richard.password])

		then:
		assertResponseStatusNoContent(response)
		User richardAfterGet = appService.reloadUser(richard)
		richardAfterGet.pinResetRequestUrl
		!richardAfterGet.verifyPinResetUrl
		!richardAfterGet.clearPinResetUrl
		!richardAfterGet.resendPinResetConfirmationCodeUrl

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Hacking attempt: Brute force try verify pin reset request'()
	{
		given:
		User richard = addRichard()
		def resetRequestResponse = appService.yonaServer.postJson(richard.pinResetRequestUrl, [:], [:], ["Yona-Password": richard.password])
		sleepTillPinResetCodeIsGenerated(richard, resetRequestResponse.json.delay)
		richard = appService.reloadUser(richard, CommonAssertions.&assertUserGetResponseDetailsPinResetRequestedAndGenerated)

		when:
		def response1TimeWrong = appService.yonaServer.postJson(richard.verifyPinResetUrl, """{"code" : "12341"}""", [:], ["Yona-Password": richard.password])
		response1TimeWrong.json.remainingAttempts == 4
		appService.yonaServer.postJson(richard.verifyPinResetUrl, """{"code" : "12342"}""", [:], ["Yona-Password": richard.password])
		appService.yonaServer.postJson(richard.verifyPinResetUrl, """{"code" : "12343"}""", [:], ["Yona-Password": richard.password])
		def response4TimesWrong = appService.yonaServer.postJson(richard.verifyPinResetUrl, """{"code" : "12344"}""", [:], ["Yona-Password": richard.password])
		def response5TimesWrong = appService.yonaServer.postJson(richard.verifyPinResetUrl, """{"code" : "12345"}""", [:], ["Yona-Password": richard.password])
		def response6TimesWrong = appService.yonaServer.postJson(richard.verifyPinResetUrl, """{"code" : "12346"}""", [:], ["Yona-Password": richard.password])
		def response7thTimeRight = appService.yonaServer.postJson(richard.verifyPinResetUrl, """{"code" : "1234"}""", [:], ["Yona-Password": richard.password])

		then:
		assertResponseStatus(response1TimeWrong, 400)
		response1TimeWrong.json.code == "error.pin.reset.request.confirmation.code.mismatch"
		response1TimeWrong.json.remainingAttempts == 4
		assertResponseStatus(response4TimesWrong, 400)
		response4TimesWrong.json.code == "error.pin.reset.request.confirmation.code.mismatch"
		response4TimesWrong.json.remainingAttempts == 1
		assertResponseStatus(response5TimesWrong, 400)
		response5TimesWrong.json.code == "error.pin.reset.request.confirmation.code.mismatch"
		response5TimesWrong.json.remainingAttempts == 0
		assertResponseStatus(response6TimesWrong, 400)
		response6TimesWrong.json.code == "error.pin.reset.request.confirmation.code.too.many.failed.attempts"
		response6TimesWrong.json.remainingAttempts == null
		assertResponseStatus(response7thTimeRight, 400)
		response7thTimeRight.json.code == "error.pin.reset.request.confirmation.code.too.many.failed.attempts"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Concurrent resend requests cause no errors'()
	{
		given:
		User richard = addRichard()
		def resetRequestResponse = appService.yonaServer.postJson(richard.pinResetRequestUrl, [:], [:], ["Yona-Password": richard.password])
		sleepTillPinResetCodeIsGenerated(richard, resetRequestResponse.json.delay)
		richard = appService.reloadUser(richard, CommonAssertions.&assertUserGetResponseDetailsPinResetRequestedAndGenerated)
		def numberOfTimes = 5

		when:
		def responses = appService.yonaServer.postJsonConcurrently(numberOfTimes, richard.resendPinResetConfirmationCodeUrl, [:], [:], ["Yona-Password": richard.password])

		then:
		responses.size() == numberOfTimes
		responses.each { assert it.status == 204 }

		cleanup:
		appService.deleteUser(richard)
	}
}
