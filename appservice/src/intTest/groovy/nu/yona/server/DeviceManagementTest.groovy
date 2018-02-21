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
import nu.yona.server.test.Device
import nu.yona.server.test.User

class DeviceManagementTest extends AbstractAppServiceIntegrationTest
{
	def 'Set and get new device request'()
	{
		given:
		User richard = addRichard()

		when:
		def newDeviceRequestPassword = "Temp password"
		def response = appService.setNewDeviceRequest(richard.mobileNumber, richard.password, newDeviceRequestPassword)

		then:
		assertResponseStatusOk(response)

		def getResponseAfter = appService.getNewDeviceRequest(richard.mobileNumber)
		assertResponseStatusOk(getResponseAfter)
		getResponseAfter.responseData.yonaPassword == null
		getResponseAfter.responseData._links.self.href == richard.newDeviceRequestUrl
		getResponseAfter.responseData._links.edit.href == richard.newDeviceRequestUrl
		getResponseAfter.responseData._links."yona:user".href
		def baseUserUrl = YonaServer.stripQueryString(richard.url)
		YonaServer.stripQueryString(getResponseAfter.responseData._links."yona:user".href) == baseUserUrl
		// The below assert checks the path fragment. If it fails, the Swagger spec needs to be updated too
		getResponseAfter.responseData._links."yona:registerDevice".href == baseUserUrl + "/devices/"

		def getWithPasswordResponseAfter = appService.getNewDeviceRequest(richard.mobileNumber, newDeviceRequestPassword)
		assertResponseStatusOk(getWithPasswordResponseAfter)
		getWithPasswordResponseAfter.responseData.yonaPassword == richard.password
		getWithPasswordResponseAfter.responseData._links.self.href == richard.newDeviceRequestUrl
		getWithPasswordResponseAfter.responseData._links.edit.href == richard.newDeviceRequestUrl
		getWithPasswordResponseAfter.responseData._links."yona:user".href
		YonaServer.stripQueryString(getWithPasswordResponseAfter.responseData._links."yona:user".href) == YonaServer.stripQueryString(richard.url)

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Richard adds new device; Bob gets the update'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob
		def firstDeviceName = richard.devices[0].name

		when:
		def newDeviceRequestPassword = "Temp password"
		def response = appService.setNewDeviceRequest(richard.mobileNumber, richard.password, newDeviceRequestPassword)

		then:
		assertResponseStatusOk(response)

		def getResponseAfter = appService.getNewDeviceRequest(richard.mobileNumber)
		assertResponseStatusOk(getResponseAfter)
		getResponseAfter.responseData.yonaPassword == null
		getResponseAfter.responseData._links.self.href == richard.newDeviceRequestUrl
		getResponseAfter.responseData._links.edit.href == richard.newDeviceRequestUrl
		getResponseAfter.responseData._links."yona:user".href
		def baseUserUrl = YonaServer.stripQueryString(richard.url)
		YonaServer.stripQueryString(getResponseAfter.responseData._links."yona:user".href) == baseUserUrl
		def registerUrl = getResponseAfter.responseData._links."yona:registerDevice".href
		// The below assert checks the path fragment. If it fails, the Swagger spec needs to be updated too
		registerUrl == baseUserUrl + "/devices/"

		def newDeviceName = "My iPhone"
		def newDeviceOs = "ANDROID"
		def registerResponse = appService.registerNewDevice(registerUrl, newDeviceRequestPassword, newDeviceName, newDeviceOs, Device.SUPPORTED_APP_VERSION)
		assertResponseStatusCreated(registerResponse)
		assertUserGetResponseDetailsWithPrivateData(registerResponse, false)

		def devices = registerResponse.responseData._embedded."yona:devices"._embedded."yona:devices"
		devices.size == 2

		def defaultDevice = (devices[0].name == newDeviceName) ? devices[1] : devices[0]
		def newDevice = (devices[0].name == newDeviceName) ? devices[0] : devices[1]

		assertDefaultOwnDevice(defaultDevice)
		assert newDevice.name == newDeviceName
		assert newDevice.operatingSystem == newDeviceOs
		assertDateTimeFormat(newDevice.registrationTime)
		assertDateFormat(newDevice.appLastOpenedDate)

		// User self-link uses the new device as "requestingDeviceId"
		def idOfNewDevice = newDevice._links.self.href - ~/.*\//
		registerResponse.responseData._links.self.href ==~ /.*requestingDeviceId=$idOfNewDevice.*/

		// App activity URL is for the new device
		registerResponse.responseData._links."yona:appActivity".href == newDevice._links.self.href + "/appActivity/"

		def bobMessagesAfterUpdate = appService.getMessages(bob)
		assertResponseStatusOk(bobMessagesAfterUpdate)
		def deviceChangeMessages = bobMessagesAfterUpdate.responseData._embedded?."yona:messages".findAll{ it."@type" == "BuddyDeviceChangeMessage" }

		deviceChangeMessages.size() == 1
		deviceChangeMessages[0]._links.self != null
		deviceChangeMessages[0]._links."yona:process" == null // Processing happens automatically these days
		deviceChangeMessages[0]._links."yona:user".href == bob.buddies[0].user.url
		deviceChangeMessages[0].message == "User added a device named '$newDeviceName'"

		User bobAfterReload = appService.reloadUser(bob)
		bobAfterReload.buddies[0].user.devices.size == 2
		if (bobAfterReload.buddies[0].user.devices[0].name == newDeviceName)
		{
			bobAfterReload.buddies[0].user.devices[1].name == firstDeviceName
		}
		else
		{
			bobAfterReload.buddies[0].user.devices[0].name == firstDeviceName
			bobAfterReload.buddies[0].user.devices[1].name == newDeviceName
		}

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Try register new device with wrong password'()
	{
		given:
		User richard = addRichard()
		def newDeviceRequestPassword = "Temp password"
		def setResponse = appService.setNewDeviceRequest(richard.mobileNumber, richard.password, newDeviceRequestPassword)
		assertResponseStatusOk(setResponse)
		def getResponseAfter = appService.getNewDeviceRequest(richard.mobileNumber)
		assertResponseStatusOk(getResponseAfter)
		def registerUrl = getResponseAfter.responseData._links."yona:registerDevice".href

		when:
		def newDeviceName = "My iPhone"
		def newDeviceOs = "ANDROID"
		def response = appService.registerNewDevice(registerUrl, "Wrong password", newDeviceName, newDeviceOs, "0.1")

		then:
		assertResponseStatus(response, 400)
		response.responseData.code == "error.device.request.invalid.password"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Try register new device while no new device request exists'()
	{
		given:
		User richard = addRichard()
		def registerUrl = YonaServer.stripQueryString(richard.url)+ "/devices/"

		when:
		def newDeviceName = "My iPhone"
		def newDeviceOs = "ANDROID"
		def response = appService.registerNewDevice(registerUrl, "Wrong password", newDeviceName, newDeviceOs, "0.1")

		then:
		assertResponseStatus(response, 400)
		response.responseData.code == "error.no.device.request.present"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Try get new device request with wrong information'()
	{
		given:
		def newDeviceRequestPassword = "Temp password"
		User richard = addRichard()
		User bob = addBob()
		appService.setNewDeviceRequest(richard.mobileNumber, richard.password, newDeviceRequestPassword)

		when:
		def responseWrongNewDeviceRequestPassword = appService.getNewDeviceRequest(richard.mobileNumber, "wrong temp password")
		def responseWrongMobileNumber = appService.getNewDeviceRequest("+31610609189", "wrong temp password")
		def responseNoNewDeviceRequestWrongPassword = appService.getNewDeviceRequest(bob.mobileNumber, "wrong temp password")
		def responseNoNewDeviceRequestNoPassword = appService.getNewDeviceRequest(bob.mobileNumber)

		then:
		assertResponseStatus(responseWrongNewDeviceRequestPassword, 400)
		responseWrongNewDeviceRequestPassword.responseData.code == "error.device.request.invalid.password"
		assertResponseStatus(responseWrongMobileNumber, 400)
		responseWrongMobileNumber.responseData.code == "error.no.device.request.present"
		assertResponseStatus(responseNoNewDeviceRequestWrongPassword, 400)
		responseNoNewDeviceRequestWrongPassword.responseData.code == "error.no.device.request.present"
		assertResponseStatus(responseNoNewDeviceRequestNoPassword, 400)
		responseNoNewDeviceRequestNoPassword.responseData.code == "error.no.device.request.present"

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Try set new device request with wrong information'()
	{
		given:
		User richard = addRichard()
		def newDeviceRequestPassword = "Temp password"
		appService.setNewDeviceRequest(richard.mobileNumber, richard.password, newDeviceRequestPassword)
		def getResponseImmmediately = appService.getNewDeviceRequest(richard.mobileNumber)
		assertResponseStatusOk(getResponseImmmediately)

		when:
		def responseWrongPassword = appService.setNewDeviceRequest(richard.mobileNumber, "foo", "Some password")
		def responseWrongMobileNumber = appService.setNewDeviceRequest("+31610609189", "foo", "Some password")

		then:
		assertResponseStatus(responseWrongPassword, 400)
		responseWrongPassword.responseData.code == "error.decrypting.data"
		def getResponseAfter = appService.getNewDeviceRequest(richard.mobileNumber, newDeviceRequestPassword)
		assertResponseStatusOk(getResponseAfter)
		getResponseAfter.responseData.yonaPassword == richard.password
		assertResponseStatus(responseWrongMobileNumber, 400)
		responseWrongMobileNumber.responseData.code == "error.decrypting.data"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Overwrite new device request'()
	{
		given:
		User richard = addRichard()
		appService.setNewDeviceRequest(richard.mobileNumber, richard.password, "Some password")

		when:
		def newDeviceRequestPassword = "Temp password"
		def response = appService.setNewDeviceRequest(richard.mobileNumber, richard.password, newDeviceRequestPassword)

		then:
		assertResponseStatusOk(response)
		def getResponseAfter = appService.getNewDeviceRequest(richard.mobileNumber, newDeviceRequestPassword)
		assertResponseStatusOk(getResponseAfter)
		getResponseAfter.responseData.yonaPassword == richard.password

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Clear new device request'()
	{
		given:
		def newDeviceRequestPassword = "Temp password"
		User richard = addRichard()
		def initialResponse = appService.setNewDeviceRequest(richard.mobileNumber, richard.password, newDeviceRequestPassword)

		when:
		def response = appService.clearNewDeviceRequest(richard.mobileNumber, richard.password)

		then:
		assertResponseStatusOk(response)
		def getResponseAfter = appService.getNewDeviceRequest(richard.mobileNumber)
		assertResponseStatus(getResponseAfter, 400)
		getResponseAfter.responseData.code == "error.no.device.request.present"

		def getWithPasswordResponseAfter = appService.getNewDeviceRequest(richard.mobileNumber, newDeviceRequestPassword)
		assertResponseStatus(getWithPasswordResponseAfter, 400)
		getWithPasswordResponseAfter.responseData.code == "error.no.device.request.present"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Try clear new device request with wrong information'()
	{
		given:
		def newDeviceRequestPassword = "Temp password"
		User richard = addRichard()
		def initialResponse = appService.setNewDeviceRequest(richard.mobileNumber, richard.password, newDeviceRequestPassword)

		when:
		def responseWrongPassword = appService.clearNewDeviceRequest(richard.mobileNumber, "foo")
		def responseWrongMobileNumber = appService.clearNewDeviceRequest("+31610609189", "foo")

		then:
		assertResponseStatus(responseWrongPassword, 400)
		responseWrongPassword.responseData.code == "error.decrypting.data"
		def getResponseAfter = appService.getNewDeviceRequest(richard.mobileNumber, newDeviceRequestPassword)
		assertResponseStatusOk(getResponseAfter)
		getResponseAfter.responseData.yonaPassword == richard.password
		assertResponseStatus(responseWrongMobileNumber, 400)
		responseWrongMobileNumber.responseData.code == "error.decrypting.data"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Richard updates his device name; Bob gets the update'()
	{
		given:
		def ts = timestamp
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob
		def updatedName = "Updated name"

		when:
		def response = appService.updateResource(richard.devices[0].editUrl, """{"name":"$updatedName"}""", ["Yona-Password" : richard.password])


		then:
		assertResponseStatusOk(response)
		def richardAfterReload = appService.reloadUser(richard, CommonAssertions.&assertUserGetResponseDetailsWithPrivateDataIgnoreDefaultDevice)

		richardAfterReload.devices.size == 1
		richardAfterReload.devices[0].name == updatedName
		richardAfterReload.devices[0].operatingSystem == "IOS"

		def bobMessagesAfterUpdate = appService.getMessages(bob)
		assertResponseStatusOk(bobMessagesAfterUpdate)
		def deviceChangeMessages = bobMessagesAfterUpdate.responseData._embedded?."yona:messages".findAll{ it."@type" == "BuddyDeviceChangeMessage"}

		deviceChangeMessages.size() == 1
		deviceChangeMessages[0]._links.self != null
		deviceChangeMessages[0]._links."yona:process" == null // Processing happens automatically these days
		deviceChangeMessages[0]._links."yona:user".href == bob.buddies[0].user.url
		deviceChangeMessages[0].message == "User renamed device 'Richard's iPhone' into '$updatedName'"

		User bobAfterReload = appService.reloadUser(bob)
		bobAfterReload.buddies[0].user.devices[0].name == updatedName

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Try update device name to device name containing a colon'()
	{
		given:
		def ts = timestamp
		User richard = addRichard()
		def updatedName = "Updated:name"

		when:
		def response = appService.updateResource(richard.devices[0].editUrl, """{"name":"$updatedName"}""", ["Yona-Password" : richard.password])

		then:
		assertResponseStatus(response, 400)
		assert response.responseData.code == "error.device.invalid.device.name"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Try update device name to existing name'()
	{
		given:
		def ts = timestamp
		User richard = addRichard()
		Device deviceToUpdate = richard.devices[0]
		def existingName = "Existing name"
		appService.addDevice(richard, existingName, "ANDROID", Device.SUPPORTED_APP_VERSION)

		when:
		def response = appService.updateResource(deviceToUpdate.editUrl, """{"name":"$existingName"}""", ["Yona-Password" : richard.password])

		then:
		assertResponseStatus(response, 400)
		assert response.responseData.code == "error.device.name.already.exists"

		cleanup:
		appService.deleteUser(richard)
	}

	def 'Richard registers two devices and delete one; Bob gets the update'()
	{
		given:
		def ts = timestamp
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob
		Device deviceToDelete = richard.devices[0]
		def remainingDeviceName = "Second device"
		def remainingOperatingSystem = "ANDROID"
		appService.addDevice(richard, remainingDeviceName, remainingOperatingSystem, Device.SUPPORTED_APP_VERSION)

		when:
		def response = appService.deleteResource(deviceToDelete.editUrl, ["Yona-Password" : richard.password])

		then:
		assertResponseStatus(response, 204) // TODO: Make this assertResponseStatusNoContent
		def richardAfterReload = appService.reloadUser(richard, CommonAssertions.&assertUserGetResponseDetailsWithPrivateDataIgnoreDefaultDevice)

		richardAfterReload.devices.size == 1
		richardAfterReload.devices[0].name == remainingDeviceName
		richardAfterReload.devices[0].operatingSystem == remainingOperatingSystem

		def bobMessagesAfterUpdate = appService.getMessages(bob)
		assertResponseStatusOk(bobMessagesAfterUpdate)
		def deviceChangeMessages = bobMessagesAfterUpdate.responseData._embedded?."yona:messages".findAll{ it."@type" == "BuddyDeviceChangeMessage" && it.message ==~ /^User deleted.*/}

		deviceChangeMessages.size() == 1
		deviceChangeMessages[0]._links.self != null
		deviceChangeMessages[0]._links."yona:process" == null // Processing happens automatically these days
		deviceChangeMessages[0]._links."yona:user".href == bob.buddies[0].user.url
		deviceChangeMessages[0].message == "User deleted device 'Richard's iPhone'"

		User bobAfterReload = appService.reloadUser(bob)
		bobAfterReload.buddies[0].user.devices.size == 1
		bobAfterReload.buddies[0].user.devices[0].name == remainingDeviceName

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Try delete last device'()
	{
		given:
		def ts = timestamp
		User richard = addRichard()
		Device deviceToDelete = richard.devices[0]

		when:
		def response = appService.deleteResource(deviceToDelete.editUrl, ["Yona-Password" : richard.password])

		then:
		assertResponseStatus(response, 400)
		assert response.responseData.code == "error.device.cannot.delete.last.one"

		cleanup:
		appService.deleteUser(richard)
	}
}
