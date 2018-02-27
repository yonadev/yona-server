/*******************************************************************************
 * Copyright (c) 2017 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.test

import groovy.json.*
import groovy.transform.ToString

@ToString(includeNames=true)
class Device
{
	private static final String SUPPORTED_APP_VERSION = "9.9.9"

	final String url
	final String editUrl
	final String ovpnProfileUrl
	final String name
	final String operatingSystem
	final String appLastOpenedDate
	final VPNProfile vpnProfile
	final boolean vpnConnected
	Device(def json)
	{
		this.name = json.name
		this.operatingSystem = json.operatingSystem
		this.appLastOpenedDate = json.appLastOpenedDate
		this.url = json._links?.self?.href
		this.editUrl = json._links?.edit?.href
		this.ovpnProfileUrl = json._links?.ovpnProfile?.href
		this.vpnProfile = (json.vpnProfile) ? new VPNProfile(json.vpnProfile) : null
		this.vpnConnected = json.vpnConnected
	}
}