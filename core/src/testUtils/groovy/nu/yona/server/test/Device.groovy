/*******************************************************************************
 * Copyright (c) 2017, 2020 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.test

import java.time.LocalDate

import groovy.transform.ToString
import nu.yona.server.YonaServer

@ToString(includeNames=true)
class Device
{
	private static final String SOME_APP_VERSION = "9.9.9"
	private static final int SUPPORTED_APP_VERSION_CODE = 999

	final String password
	final String url
	final String editUrl
	final String name
	final String operatingSystem
	final String appLastOpenedDate
	final LocalDate lastMonitoredActivityDate
	final VPNProfile vpnProfile
	final boolean vpnConnected
	final boolean requestingDevice
	final String sslRootCertCn
	final String postOpenAppEventUrl
	final String appActivityUrl
	final String postVpnStatusEventUrl
	final String sslRootCertUrl
	final String firebaseInstanceId
	final String appleMobileConfig
	Device(password, json)
	{
		this.password = password
		this.name = json.name
		this.operatingSystem = json.operatingSystem
		this.appLastOpenedDate = json.appLastOpenedDate
		this.lastMonitoredActivityDate = (json.lastMonitoredActivityDate) ? YonaServer.parseIsoDateString(json.lastMonitoredActivityDate) : null
		this.url = json._links?.self?.href
		this.editUrl = json._links?.edit?.href
		this.vpnProfile = (json.vpnProfile) ? new VPNProfile(json.vpnProfile) : null
		this.vpnConnected = json.vpnConnected
		this.requestingDevice = json.requestingDevice
		this.sslRootCertCn = json.sslRootCertCN
		this.postOpenAppEventUrl = json._links?."yona:postOpenAppEvent"?.href
		this.appActivityUrl = json._links?."yona:appActivity"?.href
		this.postVpnStatusEventUrl = json._links?."yona:postVpnStatusEvent"?.href
		this.sslRootCertUrl = json._links?."yona:sslRootCert"?.href
		this.appleMobileConfig = json._links?."yona:appleMobileConfig"?.href
		this.firebaseInstanceId = json.firebaseInstanceId
	}

	def postOpenAppEvent(AppService appService, operatingSystem = this.operatingSystem, appVersion = Device.SOME_APP_VERSION, appVersionCode = Device.SUPPORTED_APP_VERSION_CODE, locale = "en-US")
	{
		appService.createResourceWithPassword(postOpenAppEventUrl, """{"operatingSystem":"$operatingSystem", "appVersion":"$appVersion", "appVersionCode":"$appVersionCode"}""", password, [:], ["Accept-Language" : locale])
	}

	def postVpnStatus(AppService appService, boolean vpnConnected)
	{
		appService.createResourceWithPassword(postVpnStatusEventUrl, """{"vpnConnected":"$vpnConnected"}""", password)
	}
}

class VPNProfile
{
	final String vpnLoginId
	final String vpnPassword
	final String ovpnProfileUrl

	VPNProfile(def json)
	{
		this.vpnLoginId = json.vpnLoginID
		this.vpnPassword = json.vpnPassword
		this.ovpnProfileUrl = json._links."yona:ovpnProfile".href
	}

	String toString()
	{
		"VPN profile(login ID: ${vpnLoginId}, password: ${vpnPassword}, url: ${ovpnProfileUrl})"
	}
}