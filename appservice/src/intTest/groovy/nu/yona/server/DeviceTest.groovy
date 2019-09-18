/*******************************************************************************
 * Copyright (c) 2017, 2019 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import static nu.yona.server.test.CommonAssertions.*

import groovy.json.*
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
		def johnAfterNumberConfirmation = appService.confirmMobileNumber(CommonAssertions.&assertResponseStatusSuccess, johnAsCreated)

		assertInitialDeviceDetails(johnAfterNumberConfirmation, "My S8", "ANDROID")

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
		def johnAfterNumberConfirmation = appService.confirmMobileNumber(CommonAssertions.&assertResponseStatusSuccess, johnAsCreated)

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
		def johnAfterNumberConfirmation = appService.confirmMobileNumber(CommonAssertions.&assertResponseStatusSuccess, johnAsCreated)

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
		def johnAfterNumberConfirmation = appService.confirmMobileNumber(CommonAssertions.&assertResponseStatusSuccess, johnAsCreated)

		assertInitialDeviceDetails(johnAfterNumberConfirmation, "01234567890123456789", "IOS")

		cleanup:
		appService.deleteUser(johnAsCreated)
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
		assert johnAfterNumberConfirmation.devices[0].name == name
		assert johnAfterNumberConfirmation.devices[0].operatingSystem == operatingSystem
		assert johnAfterNumberConfirmation.devices[0].sslRootCertCn == "smoothwall003.yona"
		assert johnAfterNumberConfirmation.devices[0].sslRootCertUrl
		if (firebaseInstanceId)
		{
			assert johnAfterNumberConfirmation.devices[0].firebaseInstanceId == firebaseInstanceId
		}
		else if (name == "First device")
		{
			assert johnAfterNumberConfirmation.devices[0].firebaseInstanceId == null
		}
		else
		{
			assert johnAfterNumberConfirmation.devices[0].firebaseInstanceId ==~/$CommonAssertions.UUID_PATTERN/
		}
		assertEquals(johnAfterNumberConfirmation.devices[0].appLastOpenedDate, YonaServer.now.toLocalDate())

		def responseSslRootCert = appService.yonaServer.restClient.get(path: johnAfterNumberConfirmation.devices[0].sslRootCertUrl)
		assertResponseStatusOk(responseSslRootCert)
		assert responseSslRootCert.contentType == "application/pkix-cert"

		assert johnAfterNumberConfirmation.devices[0].appleMobileConfig
		def responseAppleMobileConfig = appService.yonaServer.restClient.get(path: johnAfterNumberConfirmation.devices[0].appleMobileConfig, headers: ["Yona-Password":johnAfterNumberConfirmation.password])
		assertResponseStatusOk(responseAppleMobileConfig)
		assert responseAppleMobileConfig.contentType == "application/x-apple-aspen-config"
		def appleMobileConfig = responseAppleMobileConfig.responseData.text
		assert appleMobileConfig.contains("<string>${johnAfterNumberConfirmation.devices[0].vpnProfile.vpnLoginId}\\n${johnAfterNumberConfirmation.devices[0].vpnProfile.vpnPassword}</string>")
	}

	def 'Try to create John Doe with an unsupported device operating system'()
	{
		given:
		def ts = timestamp

		when:
		User johnAsCreated = appService.addUser(
				{
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
		User johnAsCreated = appService.addUser(
				{
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
		User johnAsCreated = appService.addUser(
				{
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
		assert john.devices[0].name == "First device"
		assert john.devices[0].operatingSystem == "UNKNOWN"

		when:
		def response = appService.postAppActivityToAnalysisEngine(john, AppActivity.singleActivity("Poker App", YonaServer.now.minusHours(1), YonaServer.now))

		then:
		assertResponseStatusOk(response)

		def johnAfterAppActivity = appService.reloadUser(john, CommonAssertions.&assertUserGetResponseDetailsWithPrivateDataIgnoreDefaultDevice)
		johnAfterAppActivity.devices.size == 1
		johnAfterAppActivity.devices[0].name == "First device"
		johnAfterAppActivity.devices[0].operatingSystem == "ANDROID"

		cleanup:
		appService.deleteUser(john)
	}

	def 'Try to create John Doe with device name that contains a colon'()
	{
		given:
		def ts = timestamp

		when:
		User johnAsCreated = appService.addUser(
				{
					assertResponseStatus(it, 400)
					assert it.responseData.code == "error.device.invalid.device.name"
				}, "John", "Doe", "JD",
				makeMobileNumber(ts), "some:thing", "IOS", Device.SOME_APP_VERSION, Device.SUPPORTED_APP_VERSION_CODE)

		then:
		assert johnAsCreated == null // Creation failed
	}

	def 'John Doe posts empty app opened event to legacy URL (YD-544)'()
	{
		given:
		def ts = timestamp
		def john = createJohnDoe(ts)
		john = appService.confirmMobileNumber(CommonAssertions.&assertResponseStatusSuccess, john)

		when:
		def response = appService.createResourceWithPassword(john.postOpenAppEventUrl, "{}", john.password)

		then:
		assertResponseStatusOk(response)

		cleanup:
		appService.deleteUser(john)
	}

	def 'Richard posts app opened event with a valid operating system and app version'()
	{
		given:
		def richard = addRichard()

		when:
		def response = richard.devices[0].postOpenAppEvent(appService)

		then:
		assertResponseStatusOk(response)

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Try to post app opened event with different operating system'()
	{
		given:
		def richard = addRichard()

		when:
		def response = richard.devices[0].postOpenAppEvent(appService, "ANDROID")

		then:
		assertResponseStatus(response, 400)
		assert response.responseData.code == "error.device.cannot.switch.operating.system"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Try to post app opened event for a too old app version'()
	{
		given:
		def richard = addRichard()

		when:
		def appVersion = "9.9.9"
		def response = richard.devices[0].postOpenAppEvent(appService, richard.devices[0].operatingSystem, appVersion, 2)

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
		def richard = addRichard()

		when:
		def response = richard.devices[0].postOpenAppEvent(appService, richard.devices[0].operatingSystem, "1.0.0", -2)

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
		def john = createJohnDoe(ts)
		john = appService.confirmMobileNumber(CommonAssertions.&assertResponseStatusSuccess, john)

		when:
		def values = [ : ]
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

		def response = appService.createResourceWithPassword(john.devices[0].postOpenAppEventUrl, JsonOutput.prettyPrint(JsonOutput.toJson(values)), john.password)

		then:
		assertResponseStatus(response, responseStatus)

		cleanup:
		appService.deleteUser(john)

		where:
		operatingSystem | appVersion | appVersionCode | responseStatus
		"IOS" | "1.1" | 5000 | 200
		null | null | null | 200
		"IOS" | null | null | 400
		null | "1.1" | null | 400
		null | null | 5000 | 400
		null | "1.1" | 5000 | 400
		"IOS" | "1.1" | null | 400
		"IOS" | null | 5000 | 400
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
