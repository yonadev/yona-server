/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import groovy.json.*
import nu.yona.server.test.AppService
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
		def johnAfterNumberConfirmation = appService.confirmMobileNumber(AppService.&assertResponseStatusSuccess, johnAsCreated)

		johnAfterNumberConfirmation.devices.size == 1
		johnAfterNumberConfirmation.devices[0].name == "My S8"
		johnAfterNumberConfirmation.devices[0].operatingSystem == "ANDROID"

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
		def johnAfterNumberConfirmation = appService.confirmMobileNumber(AppService.&assertResponseStatusSuccess, johnAsCreated)

		johnAfterNumberConfirmation.devices.size == 1
		johnAfterNumberConfirmation.devices[0].name == "My iPhone X"
		johnAfterNumberConfirmation.devices[0].operatingSystem == "IOS"

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
		def johnAfterNumberConfirmation = appService.confirmMobileNumber(AppService.&assertResponseStatusSuccess, johnAsCreated)

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
					AppService.assertResponseStatus(it, 400)
					assert it.responseData.code == "error.device.unknown.operating.system"
				}, "John", "Doe", "JD",
				makeMobileNumber(ts), "My Raspberry", "RASPBIAN")

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
					AppService.assertResponseStatus(it, 400)
					assert it.responseData.code == "error.device.unknown.operating.system"
				}, "John", "Doe", "JD",
				makeMobileNumber(ts), "First device", "UNKNOWN")

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
					AppService.assertResponseStatus(it, 400)
					assert it.responseData.code == "error.device.invalid.device.name"
				}, "John", "Doe", "JD",
				makeMobileNumber(ts), "012345678901234567891", "IOS")

		then:
		assert johnAsCreated == null // Creation failed
	}

	def 'Try to create John Doe with device name that contains a colon'()
	{
		given:
		def ts = timestamp

		when:
		def johnAsCreated = appService.addUser(
				{
					AppService.assertResponseStatus(it, 400)
					assert it.responseData.code == "error.device.invalid.device.name"
				}, "John", "Doe", "JD",
				makeMobileNumber(ts), "some:thing", "IOS")

		then:
		assert johnAsCreated == null // Creation failed
	}

	private User createJohnDoe(ts, deviceName, deviceOperatingSystem)
	{
		appService.addUser(appService.&assertUserCreationResponseDetails, "John", "Doe", "JD",
				makeMobileNumber(ts), deviceName, deviceOperatingSystem)
	}
}