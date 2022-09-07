/*******************************************************************************
 * Copyright (c) 2017, 2022 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import static nu.yona.server.test.CommonAssertions.assertEquals
import static nu.yona.server.test.CommonAssertions.assertResponseStatus
import static nu.yona.server.test.CommonAssertions.assertResponseStatusNoContent
import static nu.yona.server.test.CommonAssertions.assertResponseStatusOk

import groovy.json.JsonOutput
import nu.yona.server.test.AppActivity
import nu.yona.server.test.CommonAssertions
import nu.yona.server.test.Device
import nu.yona.server.test.User

class DeviceTest extends AbstractAppServiceIntegrationTest
{
	def 'Create John Doe with an Android device'()
	{
		given:
		def ts = timestamp

		when:
		User johnAsCreated = createJohnDoe(ts, "My S8", "ANDROID")

		then:
		User johnAfterNumberConfirmation = appService.confirmMobileNumber(CommonAssertions.&assertResponseStatusSuccess, johnAsCreated)

		assertInitialDeviceDetails(johnAfterNumberConfirmation, "My S8", "ANDROID")

		// The below asserts check the path fragments. If one of these asserts fails, the Swagger spec needs to be updated too
		def baseUserUrl = YonaServer.stripQueryString(johnAfterNumberConfirmation.url)
		johnAfterNumberConfirmation.requestingDevice.postOpenAppEventUrl == baseUserUrl + "/devices/" + johnAfterNumberConfirmation.getRequestingDeviceId() + "/openApp"
		johnAfterNumberConfirmation.requestingDevice.appActivityUrl == baseUserUrl + "/devices/" + johnAfterNumberConfirmation.getRequestingDeviceId() + "/appActivity/"
		johnAfterNumberConfirmation.requestingDevice.appleMobileConfig == baseUserUrl + "/devices/" + johnAfterNumberConfirmation.getRequestingDeviceId() + "/apple.mobileconfig"

		cleanup:
		appService.deleteUser(johnAsCreated)
	}

	def 'Create John Doe with an iOS device'()
	{
		given:
		def ts = timestamp

		when:
		User johnAsCreated = createJohnDoe(ts, "My iPhone X", "IOS")

		then:
		User johnAfterNumberConfirmation = appService.confirmMobileNumber(CommonAssertions.&assertResponseStatusSuccess, johnAsCreated)

		assertInitialDeviceDetails(johnAfterNumberConfirmation, "My iPhone X", "IOS")

		cleanup:
		appService.deleteUser(johnAsCreated)
	}

	def 'Create John Doe without a device'()
	{
		given:
		def ts = timestamp

		when:
		User johnAsCreated = createJohnDoe(ts)

		then:
		User johnAfterNumberConfirmation = appService.confirmMobileNumber(CommonAssertions.&assertResponseStatusSuccess, johnAsCreated)

		assertInitialDeviceDetails(johnAfterNumberConfirmation, "First device", "UNKNOWN")

		cleanup:
		appService.deleteUser(johnAsCreated)
	}

	def 'Create John Doe with an device with the longest supported name'()
	{
		given:
		def ts = timestamp

		when:
		User johnAsCreated = createJohnDoe(ts, "01234567890123456789", "IOS")

		then:
		User johnAfterNumberConfirmation = appService.confirmMobileNumber(CommonAssertions.&assertResponseStatusSuccess, johnAsCreated)

		assertInitialDeviceDetails(johnAfterNumberConfirmation, "01234567890123456789", "IOS")

		cleanup:
		appService.deleteUser(johnAsCreated)
	}

	def 'Retrieve SSL root certificate'()
	{
		given:
		User richard = addRichard()

		when:
		assert richard.requestingDevice.sslRootCertCn
		def responseSslRootCertUrl = appService.yonaServer.getData(richard.requestingDevice.sslRootCertUrl, [:], ["Yona-Password": richard.password])

		then:
		assertResponseStatusOk(responseSslRootCertUrl)
		responseSslRootCertUrl.contentType == "application/pkix-cert"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Create John Doe with an Android device with a Firebase instance ID'()
	{
		given:
		def ts = timestamp
		def firebaseInstanceId = "d3cIznsu5VQ:APA91bGWLq7xBK1RDkpGURdliHb-S_nCBLqYnXhEWfGnItP_qGDZ6f2EF1mB66yHdBiicggV7APIWwkQXTUq_zJgwPkJtvcdqpUphYN7p8E8Sq02_ErljVApX8-n9-nvVxiyqmUg9ALZ"

		when:
		User johnAsCreated = createJohnDoe(ts, "My S8", "ANDROID", firebaseInstanceId)

		then:
		def johnAfterNumberConfirmation = appService.confirmMobileNumber(CommonAssertions.&assertResponseStatusSuccess, johnAsCreated)

		assertInitialDeviceDetails(johnAfterNumberConfirmation, "My S8", "ANDROID", firebaseInstanceId)

		cleanup:
		appService.deleteUser(johnAsCreated)
	}

	private void assertInitialDeviceDetails(User johnAfterNumberConfirmation, name, operatingSystem, firebaseInstanceId = null)
	{
		assert johnAfterNumberConfirmation.devices.size == 1
		assert johnAfterNumberConfirmation.requestingDevice.name == name
		assert johnAfterNumberConfirmation.requestingDevice.operatingSystem == operatingSystem
		assert johnAfterNumberConfirmation.requestingDevice.sslRootCertCn == "smoothwall003.yona"
		assert johnAfterNumberConfirmation.requestingDevice.sslRootCertUrl
		if (firebaseInstanceId)
		{
			assert johnAfterNumberConfirmation.requestingDevice.firebaseInstanceId == firebaseInstanceId
		}
		else if (name == "First device")
		{
			assert johnAfterNumberConfirmation.requestingDevice.firebaseInstanceId == null
		}
		else
		{
			assert johnAfterNumberConfirmation.requestingDevice.firebaseInstanceId ==~ /$CommonAssertions.UUID_PATTERN/
		}
		assertEquals(johnAfterNumberConfirmation.requestingDevice.appLastOpenedDate, YonaServer.now.toLocalDate())

		def responseSslRootCert = appService.yonaServer.getData(johnAfterNumberConfirmation.requestingDevice.sslRootCertUrl)
		assertResponseStatusOk(responseSslRootCert)
		assert responseSslRootCert.contentType == "application/pkix-cert"

		assert johnAfterNumberConfirmation.requestingDevice.vpnProfile.ovpnProfileUrl
		def responseOvpnProfile = appService.yonaServer.getData(johnAfterNumberConfirmation.requestingDevice.vpnProfile.ovpnProfileUrl)
		assertResponseStatusOk(responseOvpnProfile)
		responseOvpnProfile.contentType == "application/x-openvpn-profile"

		assert johnAfterNumberConfirmation.requestingDevice.appleMobileConfig
		def responseAppleMobileConfig = appService.yonaServer.getData(johnAfterNumberConfirmation.requestingDevice.appleMobileConfig, [:], ["Yona-Password": johnAfterNumberConfirmation.password])
		assertResponseStatusOk(responseAppleMobileConfig)
		assert responseAppleMobileConfig.contentType == "application/x-apple-aspen-config"
		def appleMobileConfig = responseAppleMobileConfig.data
		assert appleMobileConfig.contains("<string>${johnAfterNumberConfirmation.requestingDevice.vpnProfile.vpnLoginId}\\n${johnAfterNumberConfirmation.requestingDevice.vpnProfile.vpnPassword}</string>")
	}

	def 'Try to create John Doe with an unsupported device operating system'()
	{
		given:
		def ts = timestamp

		when:
		User johnAsCreated = appService.addUser({
			assertResponseStatus(it, 400)
			assert it.responseData.code == "error.device.unknown.operating.system"
		}, "John", "Doe", "JD",
				makeMobileNumber(ts), "My Raspberry", "RASPBIAN", Device.SOME_APP_VERSION, Device.SUPPORTED_APP_VERSION_CODE)

		then:
		assert johnAsCreated == null // Creation failed
	}

	def 'Try to create John Doe with device operating system UNKNOWN'()
	{
		given:
		def ts = timestamp

		when:
		User johnAsCreated = appService.addUser({
			assertResponseStatus(it, 400)
			assert it.responseData.code == "error.device.unknown.operating.system"
		}, "John", "Doe", "JD",
				makeMobileNumber(ts), "First device", "UNKNOWN", Device.SOME_APP_VERSION, Device.SUPPORTED_APP_VERSION_CODE)

		then:
		assert johnAsCreated == null // Creation failed
	}

	def 'Try to create John Doe with device name that\'s too long'()
	{
		given:
		def ts = timestamp

		when:
		User johnAsCreated = appService.addUser({
			assertResponseStatus(it, 400)
			assert it.responseData.code == "error.device.invalid.device.name"
		}, "John", "Doe", "JD",
				makeMobileNumber(ts), "012345678901234567891", "IOS", Device.SOME_APP_VERSION, Device.SUPPORTED_APP_VERSION_CODE)

		then:
		assert johnAsCreated == null // Creation failed
	}

	def 'John\'s device is autoregistered as Android upon reporting app activity'()
	{
		given:
		def ts = timestamp
		def john = createJohnDoe(ts)
		john = appService.confirmMobileNumber(CommonAssertions.&assertResponseStatusSuccess, john)
		assert john.devices.size == 1
		assert john.requestingDevice.name == "First device"
		assert john.requestingDevice.operatingSystem == "UNKNOWN"

		when:
		def response = appService.postAppActivityToAnalysisEngine(john, john.requestingDevice, AppActivity.singleActivity("Poker App", YonaServer.now.minusHours(1), YonaServer.now))

		then:
		assertResponseStatusNoContent(response)

		def johnAfterAppActivity = appService.reloadUser(john, CommonAssertions.&assertUserGetResponseDetailsIgnoreDefaultDevice)
		johnAfterAppActivity.devices.size == 1
		johnAfterAppActivity.requestingDevice.name == "First device"
		johnAfterAppActivity.requestingDevice.operatingSystem == "ANDROID"

		cleanup:
		appService.deleteUser(john)
	}

	def 'Try to create John Doe with device name that contains a colon'()
	{
		given:
		def ts = timestamp

		when:
		User johnAsCreated = appService.addUser({
			assertResponseStatus(it, 400)
			assert it.responseData.code == "error.device.invalid.device.name"
		}, "John", "Doe", "JD",
				makeMobileNumber(ts), "some:thing", "IOS", Device.SOME_APP_VERSION, Device.SUPPORTED_APP_VERSION_CODE)

		then:
		assert johnAsCreated == null // Creation failed
	}

	def 'Richard posts app opened event with a valid operating system and app version'()
	{
		given:
		def richard = addRichard()

		when:
		def response = richard.requestingDevice.postOpenAppEvent(appService)

		then:
		assertResponseStatusNoContent(response)

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Try to post app opened event with different operating system'()
	{
		given:
		User richard = addRichard()

		when:
		def response = richard.requestingDevice.postOpenAppEvent(appService, "ANDROID")

		then:
		assertResponseStatus(response, 400)
		assert response.responseData.code == "error.device.cannot.switch.operating.system"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Try to post app opened event for a too old app version'()
	{
		given:
		User richard = addRichard()

		when:
		def appVersion = "9.9.9"
		def response = richard.requestingDevice.postOpenAppEvent(appService, richard.requestingDevice.operatingSystem, appVersion, 2)

		then:
		assertResponseStatus(response, 400)
		assert response.responseData.code == "error.device.app.version.not.supported"
		assert response.responseData.message == "Yona app is out of date and must be updated. Actual version is '$appVersion' but oldest supported version for 'IOS' is '1.2'"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Try to post app opened event with an invalid version code (negative)'()
	{
		given:
		User richard = addRichard()

		when:
		def response = richard.requestingDevice.postOpenAppEvent(appService, richard.requestingDevice.operatingSystem, "1.0.0", -2)

		then:
		assertResponseStatus(response, 400)
		assert response.responseData.code == "error.device.invalid.version.code"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Try to post incomplete app opened event'(operatingSystem, appVersion, appVersionCode, responseStatus)
	{
		given:
		def ts = timestamp
		User john = createJohnDoe(ts)
		john = appService.confirmMobileNumber(CommonAssertions.&assertResponseStatusSuccess, john)

		when:
		def values = [:]
		if (operatingSystem)
		{
			values["operatingSystem"] = operatingSystem
		}
		if (appVersion)
		{
			values["appVersion"] = appVersion
		}
		if (appVersionCode)
		{
			values["appVersionCode"] = appVersionCode
		}

		def response = appService.createResourceWithPassword(john.requestingDevice.postOpenAppEventUrl, JsonOutput.prettyPrint(JsonOutput.toJson(values)), john.password)

		then:
		assertResponseStatus(response, responseStatus)

		cleanup:
		appService.deleteUser(john)

		where:
		operatingSystem | appVersion | appVersionCode | responseStatus
		"IOS"           | "1.1"      | 5000           | 204
		null            | null       | null           | 204
		"IOS"           | null       | null           | 400
		null            | "1.1"      | null           | 400
		null            | null       | 5000           | 400
		null            | "1.1"      | 5000           | 400
		"IOS"           | "1.1"      | null           | 400
		"IOS"           | null       | 5000           | 400
	}

	def 'Use different app version headers'(appVersionHeader, expectedStatus, expectedMessagePattern)
	{
		given:
		def ts = timestamp
		User john = createJohnDoe(ts)
		john = appService.confirmMobileNumber(CommonAssertions.&assertResponseStatusSuccess, john)

		when:
		def response = appService.getResourceWithPassword(john.url, john.password, [:], ["Yona-App-Version": appVersionHeader])

		then:
		assertResponseStatus(response, expectedStatus)
		expectedStatus == 200 || response.responseData.message ==~ /$expectedMessagePattern/


		cleanup:
		appService.deleteUser(john)

		where:
		appVersionHeader            | expectedStatus | expectedMessagePattern
		"ANDROID/123/1.2 build 360" | 200            | "n/a"
		"IOS/123/1.2 build 360"     | 200            | "n/a"
		"IOS/123/1.2"               | 200            | "n/a"
		"ANDROID/abc/1.2 build 360" | 400            | ".*contains an invalid version code: 'abc'.*. Must be an integer value > 0"
		"ANDROID/-1/1.2 build 360"  | 400            | ".*contains an invalid version code: '-1'.*. Must be an integer value > 0"
		"ANDROID/0/1.2 build 360"   | 400            | ".*contains an invalid version code: '0'.*. Must be an integer value > 0"
		"ANDROID/123"               | 400            | ".*is invalid: 'ANDROID/123'. Should follow the format.*"
		"ANDROID/123/a/b"           | 400            | ".*is invalid: 'ANDROID/123/a/b'. Should follow the format.*"
		""                          | 400            | ".*is invalid: ''. Should follow the format.*"
	}

	private User createJohnDoe(ts, deviceName, deviceOperatingSystem, firebaseInstanceId = null)
	{
		appService.addUser(CommonAssertions.&assertUserCreationResponseDetails, "John", "Doe", "JD",
				makeMobileNumber(ts), deviceName, deviceOperatingSystem, Device.SOME_APP_VERSION, Device.SUPPORTED_APP_VERSION_CODE, firebaseInstanceId)
	}

	private User createJohnDoe(ts)
	{
		appService.addLegacyUser(CommonAssertions.&assertUserCreationResponseDetails, "John", "Doe", "JD",
				makeMobileNumber(ts))
	}
}
