/*******************************************************************************
 * Copyright (c) 2018, 2019 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import static nu.yona.server.test.CommonAssertions.*

import nu.yona.server.test.CommonAssertions
import nu.yona.server.test.Device
import nu.yona.server.test.User

class VpnConnectionStatusEventTest extends AbstractAppServiceIntegrationTest
{
	def 'Richard posts initial VPN connected event: connection status updated and buddies informed'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob

		when:
		def response = richard.requestingDevice.postVpnStatus(appService, true)

		then:
		assertResponseStatusNoContent(response)

		User richardAfterReload = appService.reloadUser(richard)
		User bobAfterReload = appService.reloadUser(bob)
		!richard.requestingDevice.vpnConnected
		richardAfterReload.requestingDevice.vpnConnected
		!bob.buddies[0].user.devices[0].vpnConnected
		bobAfterReload.buddies[0].user.devices[0].vpnConnected

		def bobMessagesAfterUpdate = appService.getMessages(bob)
		assertResponseStatusOk(bobMessagesAfterUpdate)
		def vpnConnectionStatusChangeMessages = bobMessagesAfterUpdate.responseData._embedded?."yona:messages".findAll{ it."@type" == "BuddyVpnConnectionStatusChangeMessage"}

		vpnConnectionStatusChangeMessages.size() == 1
		vpnConnectionStatusChangeMessages[0]._links.keySet() == ["self", "edit", "yona:buddy", "yona:user", "yona:markRead"] as Set
		vpnConnectionStatusChangeMessages[0]._links."yona:user".href == bob.buddies[0].user.url
		vpnConnectionStatusChangeMessages[0].message == "User connected VPN on device '${richard.requestingDevice.name}'"

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Richard posts a VPN disconnected event: connection status updated and buddies informed'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob
		assertResponseStatusNoContent(richard.requestingDevice.postVpnStatus(appService, true))
		richard = appService.reloadUser(richard)
		bob = appService.reloadUser(bob)

		when:
		def response = richard.requestingDevice.postVpnStatus(appService, false)

		then:
		assertResponseStatusNoContent(response)

		User richardAfterReload = appService.reloadUser(richard)
		User bobAfterReload = appService.reloadUser(bob)
		richard.requestingDevice.vpnConnected
		!richardAfterReload.requestingDevice.vpnConnected
		bob.buddies[0].user.devices[0].vpnConnected
		!bobAfterReload.buddies[0].user.devices[0].vpnConnected

		def bobMessagesAfterUpdate = appService.getMessages(bob)
		assertResponseStatusOk(bobMessagesAfterUpdate)
		def vpnConnectionStatusChangeMessages = bobMessagesAfterUpdate.responseData._embedded?."yona:messages".findAll{ it."@type" == "BuddyVpnConnectionStatusChangeMessage"}

		vpnConnectionStatusChangeMessages.size() == 2
		vpnConnectionStatusChangeMessages[0]._links.self != null
		vpnConnectionStatusChangeMessages[0]._links."yona:user".href == bob.buddies[0].user.url
		vpnConnectionStatusChangeMessages[0].message == "User disconnected VPN on device '${richard.requestingDevice.name}'"

		vpnConnectionStatusChangeMessages[1]._links.self != null
		vpnConnectionStatusChangeMessages[1]._links."yona:user".href == bob.buddies[0].user.url
		vpnConnectionStatusChangeMessages[1].message == "User connected VPN on device '${richard.requestingDevice.name}'"

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Richard has two devices and juggles the VPN on them: buddies get notifications on the right devices'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		Device richardFirstDevice = richard.requestingDevice
		User bob = richardAndBob.bob
		Device richardSecondDevice = addDevice(richard, "My S9", "ANDROID")
		richard = appService.reloadUser(richard, CommonAssertions.&assertUserGetResponseDetailsIgnoreDefaultDevice)
		bob = appService.reloadUser(bob)

		def bobInitialMessagesResponse = appService.getMessages(bob) // Get will do auto-processing, making Richard's device visible to Bob
		assertResponseStatusOk(bobInitialMessagesResponse)

		when:
		def response = richardFirstDevice.postVpnStatus(appService, true)

		then:
		assertVpnStatus(response, richard, bob, true, false, 0)

		when:
		response = richardSecondDevice.postVpnStatus(appService, true)

		then:
		assertVpnStatus(response, richard, bob, true, true, 1)

		when:
		response = richardSecondDevice.postVpnStatus(appService, false)

		then:
		assertVpnStatus(response, richard, bob, true, false, 1)

		when:
		response = richardFirstDevice.postVpnStatus(appService, false)

		then:
		assertVpnStatus(response, richard, bob, false, false, 0)

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	void assertVpnStatus(def response, User richard, User bob, boolean firstDeviceStatus, boolean secondDeviceStatus, def lastToggledDevice)
	{
		assertResponseStatusNoContent(response)

		richard = appService.reloadUser(richard, CommonAssertions.&assertUserGetResponseDetailsIgnoreDefaultDevice)
		Device richardFirstDevice = richard.devices.find { it.name == "Richard's iPhone" }
		Device richardSecondDevice = richard.devices.find { it.name == "My S9" }

		assert richardFirstDevice.vpnConnected == firstDeviceStatus
		assert richardSecondDevice.vpnConnected == secondDeviceStatus

		bob = appService.reloadUser(bob)
		Device bobFirstDeviceRichard = bob.buddies[0].user.devices.find { it.name == "Richard's iPhone" }
		Device bobSecondDeviceRichard = bob.buddies[0].user.devices.find { it.name == "My S9" }

		assert bobFirstDeviceRichard.vpnConnected == firstDeviceStatus
		assert bobSecondDeviceRichard.vpnConnected == secondDeviceStatus

		def bobMessagesAfterUpdate = appService.getMessages(bob)
		assertResponseStatusOk(bobMessagesAfterUpdate)
		def vpnConnectionStatusChangeMessages = bobMessagesAfterUpdate.responseData._embedded?."yona:messages".findAll{ it."@type" == "BuddyVpnConnectionStatusChangeMessage"}

		def expectedDeviceName = (lastToggledDevice == 0) ? richardFirstDevice.name : richardSecondDevice.name
		def expectedStatus = (lastToggledDevice == 0) ? firstDeviceStatus : secondDeviceStatus
		def action = (expectedStatus) ? "connected" : "disconnected"
		assert vpnConnectionStatusChangeMessages[0].message == "User ${action} VPN on device '${expectedDeviceName}'"
	}

	Device addDevice(User user, newDeviceName, newDeviceOs)
	{
		def newDeviceRequestPassword = "Temp password"
		def response = appService.setNewDeviceRequest(user.mobileNumber, user.password, newDeviceRequestPassword)
		assertResponseStatusNoContent(response)

		def getResponseAfter = appService.getNewDeviceRequest(user.mobileNumber)
		assertResponseStatusOk(getResponseAfter)

		def registerUrl = getResponseAfter.responseData._links."yona:registerDevice".href

		def registerResponse = appService.registerNewDevice(registerUrl, newDeviceRequestPassword, newDeviceName, newDeviceOs)
		assertResponseStatusCreated(registerResponse)

		def devices = registerResponse.responseData._embedded."yona:devices"._embedded."yona:devices"

		assert devices.size == 2
		def newDeviceJson = (devices[0].name == newDeviceName) ? devices[0] : devices[1]

		new Device(user.password, newDeviceJson)
	}

	def 'Richard posts second VPN disconnected event: no message to buddies'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob
		richard = appService.reloadUser(richard)
		bob = appService.reloadUser(bob)
		def bobInitialMessagesResponse = appService.getMessages(bob)
		assertResponseStatusOk(bobInitialMessagesResponse)
		def initialVpnConnectionStatusChangeMessages = bobInitialMessagesResponse.responseData._embedded?."yona:messages".findAll{ it."@type" == "BuddyVpnConnectionStatusChangeMessage"}

		when:
		def response = richard.requestingDevice.postVpnStatus(appService, false)

		then:
		assertResponseStatusNoContent(response)

		User richardAfterReload = appService.reloadUser(richard)
		User bobAfterReload = appService.reloadUser(bob)
		!richard.requestingDevice.vpnConnected
		!richardAfterReload.requestingDevice.vpnConnected
		!bob.buddies[0].user.devices[0].vpnConnected
		!bobAfterReload.buddies[0].user.devices[0].vpnConnected

		def bobMessagesAfterUpdate = appService.getMessages(bob)
		assertResponseStatusOk(bobMessagesAfterUpdate)
		def vpnConnectionStatusChangeMessages = bobMessagesAfterUpdate.responseData._embedded?."yona:messages".findAll{ it."@type" == "BuddyVpnConnectionStatusChangeMessage"}

		vpnConnectionStatusChangeMessages == initialVpnConnectionStatusChangeMessages

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}

	def 'Richard posts second VPN connected event: no message to buddies'()
	{
		given:
		def richardAndBob = addRichardAndBobAsBuddies()
		User richard = richardAndBob.richard
		User bob = richardAndBob.bob
		assertResponseStatusNoContent(richard.requestingDevice.postVpnStatus(appService, true))
		richard = appService.reloadUser(richard)
		bob = appService.reloadUser(bob)
		def bobInitialMessagesResponse = appService.getMessages(bob)
		assertResponseStatusOk(bobInitialMessagesResponse)
		def initialVpnConnectionStatusChangeMessages = bobInitialMessagesResponse.responseData._embedded?."yona:messages".findAll{ it."@type" == "BuddyVpnConnectionStatusChangeMessage"}

		when:
		def response = richard.requestingDevice.postVpnStatus(appService, true)

		then:
		assertResponseStatusNoContent(response)

		User richardAfterReload = appService.reloadUser(richard)
		User bobAfterReload = appService.reloadUser(bob)
		richard.requestingDevice.vpnConnected
		richardAfterReload.requestingDevice.vpnConnected
		bob.buddies[0].user.devices[0].vpnConnected
		bobAfterReload.buddies[0].user.devices[0].vpnConnected

		def bobMessagesAfterUpdate = appService.getMessages(bob)
		assertResponseStatusOk(bobMessagesAfterUpdate)
		def vpnConnectionStatusChangeMessages = bobMessagesAfterUpdate.responseData._embedded?."yona:messages".findAll{ it."@type" == "BuddyVpnConnectionStatusChangeMessage"}

		vpnConnectionStatusChangeMessages == initialVpnConnectionStatusChangeMessages

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
	}
}