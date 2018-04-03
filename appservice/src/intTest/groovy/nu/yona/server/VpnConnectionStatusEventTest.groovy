/*******************************************************************************
 * Copyright (c) 2018 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server

import static nu.yona.server.test.CommonAssertions.*

import groovy.json.*
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
		def response = richard.devices[0].postVpnStatus(appService, true)

		then:
		assertResponseStatusNoContent(response)

		User richardAfterReload = appService.reloadUser(richard)
		User bobAfterReload = appService.reloadUser(bob)
		!richard.devices[0].vpnConnected
		richardAfterReload.devices[0].vpnConnected
		!bob.buddies[0].user.devices[0].vpnConnected
		bobAfterReload.buddies[0].user.devices[0].vpnConnected

		def bobMessagesAfterUpdate = appService.getMessages(bob)
		assertResponseStatusOk(bobMessagesAfterUpdate)
		def vpnConnectionStatusChangeMessages = bobMessagesAfterUpdate.responseData._embedded?."yona:messages".findAll{ it."@type" == "BuddyVpnConnectionStatusChangeMessage"}

		vpnConnectionStatusChangeMessages.size() == 1
		vpnConnectionStatusChangeMessages[0]._links.self != null
		vpnConnectionStatusChangeMessages[0]._links."yona:user".href == bob.buddies[0].user.url
		vpnConnectionStatusChangeMessages[0].message == "User connected VPN on device '${richard.devices[0].name}'"

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
		assertResponseStatusNoContent(richard.devices[0].postVpnStatus(appService, true))
		richard = appService.reloadUser(richard)
		bob = appService.reloadUser(bob)

		when:
		def response = richard.devices[0].postVpnStatus(appService, false)

		then:
		assertResponseStatusNoContent(response)

		User richardAfterReload = appService.reloadUser(richard)
		User bobAfterReload = appService.reloadUser(bob)
		richard.devices[0].vpnConnected
		!richardAfterReload.devices[0].vpnConnected
		bob.buddies[0].user.devices[0].vpnConnected
		!bobAfterReload.buddies[0].user.devices[0].vpnConnected

		def bobMessagesAfterUpdate = appService.getMessages(bob)
		assertResponseStatusOk(bobMessagesAfterUpdate)
		def vpnConnectionStatusChangeMessages = bobMessagesAfterUpdate.responseData._embedded?."yona:messages".findAll{ it."@type" == "BuddyVpnConnectionStatusChangeMessage"}

		vpnConnectionStatusChangeMessages.size() == 2
		vpnConnectionStatusChangeMessages[0]._links.self != null
		vpnConnectionStatusChangeMessages[0]._links."yona:user".href == bob.buddies[0].user.url
		vpnConnectionStatusChangeMessages[0].message == "User disconnected VPN on device '${richard.devices[0].name}'"

		vpnConnectionStatusChangeMessages[1]._links.self != null
		vpnConnectionStatusChangeMessages[1]._links."yona:user".href == bob.buddies[0].user.url
		vpnConnectionStatusChangeMessages[1].message == "User connected VPN on device '${richard.devices[0].name}'"

		cleanup:
		appService.deleteUser(richard)
		appService.deleteUser(bob)
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
		def response = richard.devices[0].postVpnStatus(appService, false)

		then:
		assertResponseStatusNoContent(response)

		User richardAfterReload = appService.reloadUser(richard)
		User bobAfterReload = appService.reloadUser(bob)
		!richard.devices[0].vpnConnected
		!richardAfterReload.devices[0].vpnConnected
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
		assertResponseStatusNoContent(richard.devices[0].postVpnStatus(appService, true))
		richard = appService.reloadUser(richard)
		bob = appService.reloadUser(bob)
		def bobInitialMessagesResponse = appService.getMessages(bob)
		assertResponseStatusOk(bobInitialMessagesResponse)
		def initialVpnConnectionStatusChangeMessages = bobInitialMessagesResponse.responseData._embedded?."yona:messages".findAll{ it."@type" == "BuddyVpnConnectionStatusChangeMessage"}

		when:
		def response = richard.devices[0].postVpnStatus(appService, true)

		then:
		assertResponseStatusNoContent(response)

		User richardAfterReload = appService.reloadUser(richard)
		User bobAfterReload = appService.reloadUser(bob)
		richard.devices[0].vpnConnected
		richardAfterReload.devices[0].vpnConnected
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