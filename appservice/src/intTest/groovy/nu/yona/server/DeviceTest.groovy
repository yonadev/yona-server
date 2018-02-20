/*******************************************************************************
 * Copyright (c) 2017, 2018 Stichting Yona Foundation
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
		def johnAsCreated = createJohnDoe(ts, "My S8", "ANDROID")

		then:
		def johnAfterNumberConfirmation = appService.confirmMobileNumber(CommonAssertions.&assertResponseStatusSuccess, johnAsCreated)

		johnAfterNumberConfirmation.devices.size == 1
		johnAfterNumberConfirmation.devices[0].name == "My S8"
		johnAfterNumberConfirmation.devices[0].operatingSystem == "ANDROID"
		assertEquals(johnAfterNumberConfirmation.devices[0].appLastOpenedDate, YonaServer.now.toLocalDate())

		cleanup:
		appService.deleteUser(johnAsCreated)
	}

	def 'Create John Doe with an iOS device'()
	{
		given:
		def ts = timestamp

		when:
		def johnAsCreated = createJohnDoe(ts, "My iPhone X", "IOS")

		then:
		def johnAfterNumberConfirmation = appService.confirmMobileNumber(CommonAssertions.&assertResponseStatusSuccess, johnAsCreated)

		johnAfterNumberConfirmation.devices.size == 1
		johnAfterNumberConfirmation.devices[0].name == "My iPhone X"
		johnAfterNumberConfirmation.devices[0].operatingSystem == "IOS"
		assertEquals(johnAfterNumberConfirmation.devices[0].appLastOpenedDate, YonaServer.now.toLocalDate())

		cleanup:
		appService.deleteUser(johnAsCreated)
	}

	def 'Create John Doe without a device'()
	{
		given:
		def ts = timestamp

		when:
		def johnAsCreated = createJohnDoe(ts)

		then:
		def johnAfterNumberConfirmation = appService.confirmMobileNumber(CommonAssertions.&assertResponseStatusSuccess, johnAsCreated)

		johnAfterNumberConfirmation.devices.size == 1
		johnAfterNumberConfirmation.devices[0].name == "First device"
		johnAfterNumberConfirmation.devices[0].operatingSystem == "UNKNOWN"
		assertEquals(johnAfterNumberConfirmation.devices[0].appLastOpenedDate, YonaServer.now.toLocalDate())

		cleanup:
		appService.deleteUser(johnAsCreated)
	}

	def 'Create John Doe with an device with the longest supported name'()
	{
		given:
		def ts = timestamp

		when:
		def johnAsCreated = createJohnDoe(ts, "01234567890123456789", "IOS")

		then:
		def johnAfterNumberConfirmation = appService.confirmMobileNumber(CommonAssertions.&assertResponseStatusSuccess, johnAsCreated)

		johnAfterNumberConfirmation.devices.size == 1
		johnAfterNumberConfirmation.devices[0].name == "01234567890123456789"
		johnAfterNumberConfirmation.devices[0].operatingSystem == "IOS"

		cleanup:
		appService.deleteUser(johnAsCreated)
	}

	def 'Try to create John Doe with an unsupported device operating system'()
	{
		given:
		def ts = timestamp

		when:
		def johnAsCreated = appService.addUser(
				{
					assertResponseStatus(it, 400)
					assert it.responseData.code == "error.device.unknown.operating.system"
				}, "John", "Doe", "JD",
				makeMobileNumber(ts), "My Raspberry", "RASPBIAN", Device.SUPPORTED_APP_VERSION)

		then:
		assert johnAsCreated == null // Creation failed
	}

	def 'Try to create John Doe with device operating system UNKNOWN'()
	{
		given:
		def ts = timestamp

		when:
		def johnAsCreated = appService.addUser(
				{
					assertResponseStatus(it, 400)
					assert it.responseData.code == "error.device.unknown.operating.system"
				}, "John", "Doe", "JD",
				makeMobileNumber(ts), "First device", "UNKNOWN", Device.SUPPORTED_APP_VERSION)

		then:
		assert johnAsCreated == null // Creation failed
	}

	def 'Try to create John Doe with device name that\'s too long'()
	{
		given:
		def ts = timestamp

		when:
		def johnAsCreated = appService.addUser(
				{
					assertResponseStatus(it, 400)
					assert it.responseData.code == "error.device.invalid.device.name"
				}, "John", "Doe", "JD",
				makeMobileNumber(ts), "012345678901234567891", "IOS", Device.SUPPORTED_APP_VERSION)

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
		def johnAsCreated = appService.addUser(
				{
					assertResponseStatus(it, 400)
					assert it.responseData.code == "error.device.invalid.device.name"
				}, "John", "Doe", "JD",
				makeMobileNumber(ts), "some:thing", "IOS", Device.SUPPORTED_APP_VERSION)

		then:
		assert johnAsCreated == null // Creation failed
	}

	def 'Richard posts empty app opened event'()
	{
		given:
		def richard = addRichard()

		when:
		def response = appService.createResourceWithPassword(richard.postOpenAppEventUrl, "{}", richard.password)

		then:
		assertResponseStatusOk(response)

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Richard posts app opened event with a valid operating system and app version'()
	{
		given:
		def richard = addRichard()

		when:
		def response = appService.createResourceWithPassword(richard.postOpenAppEventUrl, """{"operatingSystem":"IOS", "appVersion":"$Device.SUPPORTED_APP_VERSION"}""", richard.password)

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
		def response = appService.createResourceWithPassword(richard.postOpenAppEventUrl, """{"operatingSystem":"ANDROID", "appVersion":"$Device.SUPPORTED_APP_VERSION"}""", richard.password)

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
		def response = appService.createResourceWithPassword(richard.postOpenAppEventUrl, """{"operatingSystem":"ANDROID", "appVersion":"0.0.1"}""", richard.password)

		then:
		assertResponseStatus(response, 400)
		assert response.responseData.code == "error.device.app.version.not.supported"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Try to post app opened event with an invalid version string (not semver)'()
	{
		given:
		def richard = addRichard()

		when:
		def response = appService.createResourceWithPassword(richard.postOpenAppEventUrl, """{"operatingSystem":"ANDROID", "appVersion":"1.0"}""", richard.password)

		then:
		assertResponseStatus(response, 400)
		assert response.responseData.code == "error.device.invalid.version.string"

		cleanup:
		appService.deleteUser(richard)
	}

	private User createJohnDoe(ts, deviceName, deviceOperatingSystem)
	{
		appService.addUser(CommonAssertions.&assertUserCreationResponseDetails, "John", "Doe", "JD",
				makeMobileNumber(ts), deviceName, deviceOperatingSystem, Device.SUPPORTED_APP_VERSION)
	}

	private User createJohnDoe(ts)
	{
		appService.addUser(CommonAssertions.&assertUserCreationResponseDetails, "John", "Doe", "JD",
				makeMobileNumber(ts))
	}
}
