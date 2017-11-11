/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server


import java.time.Duration

import groovy.json.*
import nu.yona.server.test.User

class PinResetRequestTest extends AbstractAppServiceIntegrationTest
{
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

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Request pin reset'()
	{
		given:
		User richard = addRichard()

		when:
		def response = appService.yonaServer.postJson(richard.pinResetRequestUrl, [:], ["Yona-Password" : richard.password, "Accept-Language" : "nl-NL"])

		then:
		response.status == 200
		response.responseData.delay == "PT10S"
		User  richardAfterGet = appService.reloadUser(richard, appService.&assertUserGetResponseDetailsWithPrivateDataPinResetRequestedNotGenerated)
		!richardAfterGet.pinResetRequestUrl

		!richardAfterGet.verifyPinResetUrl
		!richardAfterGet.clearPinResetUrl

		sleepTillPinResetCodeIsGenerated(richard, response.responseData.delay)
		User  richardAfterDelayedGet = appService.reloadUser(richard, appService.&assertUserGetResponseDetailsWithPrivateDataPinResetRequestedAndGenerated)
		// The below asserts check the path fragments. If one of these asserts fails, the Swagger spec needs to be updated too
		richardAfterDelayedGet.verifyPinResetUrl == richard.url + "/pinResetRequest/verify"
		richardAfterDelayedGet.clearPinResetUrl == richard.url + "/pinResetRequest/clear"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Request pin reset twice'()
	{
		given:
		User richard = addRichard()
		def firstResponse = appService.yonaServer.postJson(richard.pinResetRequestUrl, [:], ["Yona-Password" : richard.password, "Accept-Language" : "nl-NL"])
		assert firstResponse.status == 200
		sleepTillPinResetCodeIsGenerated(richard, firstResponse.responseData.delay)
		richard = appService.reloadUser(richard, appService.&assertUserGetResponseDetailsWithPrivateDataPinResetRequestedAndGenerated)
		assert richard.clearPinResetUrl == richard.url + "/pinResetRequest/clear"
		assert appService.yonaServer.postJson(richard.clearPinResetUrl, [:], ["Yona-Password" : richard.password]).status == 200
		richard = appService.reloadUser(richard)
		assert richard.pinResetRequestUrl != null

		when:
		def response = appService.yonaServer.postJson(richard.pinResetRequestUrl, [:], ["Yona-Password" : richard.password, "Accept-Language" : "nl-NL"])

		then:
		response.status == 200
		response.responseData.delay == "PT10S"
		User  richardAfterGet = appService.reloadUser(richard, appService.&assertUserGetResponseDetailsWithPrivateDataPinResetRequestedNotGenerated)
		!richardAfterGet.pinResetRequestUrl

		!richardAfterGet.verifyPinResetUrl
		!richardAfterGet.clearPinResetUrl

		sleepTillPinResetCodeIsGenerated(richard, response.responseData.delay)
		User  richardAfterDelayedGet = appService.reloadUser(richard, appService.&assertUserGetResponseDetailsWithPrivateDataPinResetRequestedAndGenerated)
		// The below asserts check the path fragments. If one of these asserts fails, the Swagger spec needs to be updated too
		richardAfterDelayedGet.verifyPinResetUrl == richard.url + "/pinResetRequest/verify"
		richardAfterDelayedGet.clearPinResetUrl == richard.url + "/pinResetRequest/clear"


		cleanup:
		appService.deleteUser(richard)
	}

	private void sleepTillPinResetCodeIsGenerated(User user, def delayString)
	{
		long millis = Duration.parse(delayString).toMillis() + 2000 // Add 2 seconds margin, to be sure it's completed
		println("$YonaServer.now: sleepTillPinResetCodeIsGenerated: delayString=$delayString, user.url=$user.url, millis: $millis. Entering sleep")
		sleep(millis)
		println("$YonaServer.now: sleepTillPinResetCodeIsGenerated: sleep completed")
	}

	def 'Verify pin reset confirmation code'()
	{
		given:
		User richard = addRichard()
		def resetRequestResponse = appService.yonaServer.postJson(richard.pinResetRequestUrl, [:], ["Yona-Password" : richard.password])
		sleepTillPinResetCodeIsGenerated(richard, resetRequestResponse.responseData.delay)
		richard = appService.reloadUser(richard, appService.&assertUserGetResponseDetailsWithPrivateDataPinResetRequestedAndGenerated)

		when:
		def response = appService.yonaServer.postJson(richard.verifyPinResetUrl, """{"code" : "1234"}""", ["Yona-Password" : richard.password])

		then:
		response.status == 200
		User  richardAfterGet = appService.reloadUser(richard, appService.&assertUserGetResponseDetailsWithPrivateDataPinResetRequestedAndGenerated)
		!richardAfterGet.pinResetRequestUrl
		richardAfterGet.verifyPinResetUrl
		richardAfterGet.clearPinResetUrl

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Hacking attempt: Try to verify pin reset confirmation code before end of delay period'()
	{
		given:
		User richard = addRichard()
		def responsePost = appService.yonaServer.postJson(richard.pinResetRequestUrl, [:], ["Yona-Password" : richard.password])
		sleepTillMidOfPinResetCodeGenerationInterval(richard, responsePost.responseData.delay)

		when:
		def response = appService.yonaServer.postJson(richard.url + "/pinResetRequest/verify", """{"code" : "1234"}""", ["Yona-Password" : richard.password])

		then:
		response.status == 400
		response.responseData.code == "error.pin.reset.request.confirmation.code.mismatch"

		cleanup:
		appService.deleteUser(richard)
	}

	private void sleepTillMidOfPinResetCodeGenerationInterval(User user, def delayString)
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
		def resetRequestResponse = appService.yonaServer.postJson(richard.pinResetRequestUrl, [:], ["Yona-Password" : richard.password])
		sleepTillPinResetCodeIsGenerated(richard, resetRequestResponse.responseData.delay)
		richard = appService.reloadUser(richard, appService.&assertUserGetResponseDetailsWithPrivateDataPinResetRequestedAndGenerated)

		when:
		def response = appService.yonaServer.postJson(richard.clearPinResetUrl, [:], ["Yona-Password" : richard.password])

		then:
		response.status == 200
		User  richardAfterGet = appService.reloadUser(richard)
		richardAfterGet.pinResetRequestUrl
		!richardAfterGet.verifyPinResetUrl
		!richardAfterGet.clearPinResetUrl
		def verifyPinResetAttemptResponse = appService.yonaServer.postJson(richard.verifyPinResetUrl, """{"code" : "1234"}""", ["Yona-Password" : richard.password])
		verifyPinResetAttemptResponse.status == 400
		verifyPinResetAttemptResponse.responseData.code == "error.pin.reset.request.confirmation.code.not.set"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Clear pin reset request after verification'()
	{
		given:
		User richard = addRichard()
		def resetRequestResponse = appService.yonaServer.postJson(richard.pinResetRequestUrl, [:], ["Yona-Password" : richard.password])
		sleepTillPinResetCodeIsGenerated(richard, resetRequestResponse.responseData.delay)
		appService.yonaServer.postJson(richard.verifyPinResetUrl, """{"code" : "1234"}""", ["Yona-Password" : richard.password])
		richard = appService.reloadUser(richard, appService.&assertUserGetResponseDetailsWithPrivateDataPinResetRequestedAndGenerated)

		when:
		def response = appService.yonaServer.postJson(richard.clearPinResetUrl, [:], ["Yona-Password" : richard.password])

		then:
		response.status == 200
		User  richardAfterGet = appService.reloadUser(richard)
		richardAfterGet.pinResetRequestUrl
		!richardAfterGet.verifyPinResetUrl
		!richardAfterGet.clearPinResetUrl

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Hacking attempt: Brute force try verify pin reset request'()
	{
		given:
		User richard = addRichard()
		def resetRequestResponse = appService.yonaServer.postJson(richard.pinResetRequestUrl, [:], ["Yona-Password" : richard.password])
		sleepTillPinResetCodeIsGenerated(richard, resetRequestResponse.responseData.delay)
		richard = appService.reloadUser(richard, appService.&assertUserGetResponseDetailsWithPrivateDataPinResetRequestedAndGenerated)

		when:
		def response1TimeWrong = appService.yonaServer.postJson(richard.verifyPinResetUrl, """{"code" : "12341"}""", ["Yona-Password" : richard.password])
		response1TimeWrong.responseData.remainingAttempts == 4
		appService.yonaServer.postJson(richard.verifyPinResetUrl, """{"code" : "12342"}""", ["Yona-Password" : richard.password])
		appService.yonaServer.postJson(richard.verifyPinResetUrl, """{"code" : "12343"}""", ["Yona-Password" : richard.password])
		def response4TimesWrong = appService.yonaServer.postJson(richard.verifyPinResetUrl, """{"code" : "12344"}""", ["Yona-Password" : richard.password])
		def response5TimesWrong = appService.yonaServer.postJson(richard.verifyPinResetUrl, """{"code" : "12345"}""", ["Yona-Password" : richard.password])
		def response6TimesWrong = appService.yonaServer.postJson(richard.verifyPinResetUrl, """{"code" : "12346"}""", ["Yona-Password" : richard.password])
		def response7thTimeRight = appService.yonaServer.postJson(richard.verifyPinResetUrl, """{"code" : "1234"}""", ["Yona-Password" : richard.password])

		then:
		response1TimeWrong.status == 400
		response1TimeWrong.responseData.code == "error.pin.reset.request.confirmation.code.mismatch"
		response1TimeWrong.responseData.remainingAttempts == 4
		response4TimesWrong.status == 400
		response4TimesWrong.responseData.code == "error.pin.reset.request.confirmation.code.mismatch"
		response4TimesWrong.responseData.remainingAttempts == 1
		response5TimesWrong.status == 400
		response5TimesWrong.responseData.code == "error.pin.reset.request.confirmation.code.mismatch"
		response5TimesWrong.responseData.remainingAttempts == 0
		response6TimesWrong.status == 400
		response6TimesWrong.responseData.code == "error.pin.reset.request.confirmation.code.too.many.failed.attempts"
		response6TimesWrong.responseData.remainingAttempts == null
		response7thTimeRight.status == 400
		response7thTimeRight.responseData.code == "error.pin.reset.request.confirmation.code.too.many.failed.attempts"

		cleanup:
		appService.deleteUser(richard)
	}
}
