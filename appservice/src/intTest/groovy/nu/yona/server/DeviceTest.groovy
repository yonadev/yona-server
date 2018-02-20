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
					assertResponseStatus(it, 400)
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
					assertResponseStatus(it, 400)
					assert it.responseData.code == "error.device.invalid.device.name"
				}, "John", "Doe", "JD",
				makeMobileNumber(ts), "012345678901234567891", "IOS")

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
				makeMobileNumber(ts), "some:thing", "IOS")

		then:
		assert johnAsCreated == null // Creation failed
	}

	def 'Update device name'()
	{
		given:
		def ts = timestamp
		User john = createJohnDoe(ts, "My S8", "ANDROID")
		john = appService.confirmMobileNumber(CommonAssertions.&assertResponseStatusSuccess, john)
		def updatedName = "Updated name"

		when:
		def response = appService.updateResource(john.devices[0].editUrl, """{"name":"$updatedName"}""", ["Yona-Password" : john.password])


		then:
		assertResponseStatusOk(response)
		def johnAfterReload = appService.reloadUser(john, CommonAssertions.&assertUserGetResponseDetailsWithPrivateDataIgnoreDefaultDevice)

		johnAfterReload.devices.size == 1
		johnAfterReload.devices[0].name == updatedName
		johnAfterReload.devices[0].operatingSystem == "ANDROID"

		cleanup:
		appService.deleteUser(john)
	}

	def 'Try update device name to device name containing a colon'()
	{
		given:
		def ts = timestamp
		User john = createJohnDoe(ts, "My S8", "ANDROID")
		john = appService.confirmMobileNumber(CommonAssertions.&assertResponseStatusSuccess, john)
		def updatedName = "Updated:name"

		when:
		def response = appService.updateResource(john.devices[0].editUrl, """{"name":"$updatedName"}""", ["Yona-Password" : john.password])

		then:
		assertResponseStatus(response, 400)
		assert response.responseData.code == "error.device.invalid.device.name"

		cleanup:
		appService.deleteUser(john)
	}

	def 'Try update device name to existing name'()
	{
		given:
		def ts = timestamp
		User john = createJohnDoe(ts, "My S8", "ANDROID")
		john = appService.confirmMobileNumber(CommonAssertions.&assertResponseStatusSuccess, john)
		Device deviceToUpdate = john.devices[0]
		def existingName = "Existing name"
		appService.addDevice(john, existingName, "IOS", "1.0")

		when:
		def response = appService.updateResource(deviceToUpdate.editUrl, """{"name":"$existingName"}""", ["Yona-Password" : john.password])

		then:
		assertResponseStatus(response, 400)
		assert response.responseData.code == "error.device.name.already.exists"

		cleanup:
		appService.deleteUser(john)
	}

	def 'Create John Doe with two devices and delete one'()
	{
		given:
		def ts = timestamp
		User john = createJohnDoe(ts, "My S8", "ANDROID")
		john = appService.confirmMobileNumber(CommonAssertions.&assertResponseStatusSuccess, john)
		Device deviceToDelete = john.devices[0]
		def remainingDeviceName = "Second device"
		def remainingOperatingSystem = "IOS"
		appService.addDevice(john, remainingDeviceName, remainingOperatingSystem, "1.0")

		when:
		def response = appService.deleteResource(deviceToDelete.editUrl, ["Yona-Password" : john.password])


		then:
		assertResponseStatus(response, 204) // TODO: Make this assertResponseStatusNoContent
		def johnAfterReload = appService.reloadUser(john, CommonAssertions.&assertUserGetResponseDetailsWithPrivateDataIgnoreDefaultDevice)

		johnAfterReload.devices.size == 1
		johnAfterReload.devices[0].name == remainingDeviceName
		johnAfterReload.devices[0].operatingSystem == remainingOperatingSystem

		cleanup:
		appService.deleteUser(john)
	}

	def 'Try delete last device'()
	{
		given:
		def ts = timestamp
		User john = createJohnDoe(ts, "My S8", "ANDROID")
		john = appService.confirmMobileNumber(CommonAssertions.&assertResponseStatusSuccess, john)
		Device deviceToDelete = john.devices[0]

		when:
		def response = appService.deleteResource(deviceToDelete.editUrl, ["Yona-Password" : john.password])

		then:
		assertResponseStatus(response, 400)
		assert response.responseData.code == "error.device.cannot.delete.last.one"

		cleanup:
		appService.deleteUser(john)
	}

	private User createJohnDoe(ts, deviceName, deviceOperatingSystem)
	{
		appService.addUser(CommonAssertions.&assertUserCreationResponseDetails, "John", "Doe", "JD",
				makeMobileNumber(ts), deviceName, deviceOperatingSystem)
	}

	private User createJohnDoe(ts)
	{
		appService.addUser(CommonAssertions.&assertUserCreationResponseDetails, "John", "Doe", "JD",
				makeMobileNumber(ts))
	}
}
