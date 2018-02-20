/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.test

import java.time.LocalDate
import java.time.ZonedDateTime

import groovy.json.*
import groovy.transform.ToString
import net.sf.json.groovy.JsonSlurper
import nu.yona.server.YonaServer

@ToString(includeNames=true)
class User
{
	final ZonedDateTime creationTime
	final LocalDate appLastOpenedDate
	final LocalDate lastMonitoredActivityDate
	final String firstName
	final String lastName
	final String mobileNumber
	String emailAddress
	String deviceName
	String deviceOperatingSystem
	String deviceAppVersion
	final String mobileNumberConfirmationUrl
	final String resendMobileNumberConfirmationCodeUrl
	final String postOpenAppEventUrl
	final boolean hasPrivateData
	final String nickname
	final String userPhotoUrl
	final String editUserPhotoUrl
	final List<Goal> goals
	final List<Buddy> buddies
	final List<Device> devices
	final VPNProfile vpnProfile
	final String url
	final String editUrl
	final String buddiesUrl
	final String goalsUrl
	final String messagesUrl
	final String dailyActivityReportsUrl
	final String dailyActivityReportsWithBuddiesUrl
	final String weeklyActivityReportsUrl
	final String newDeviceRequestUrl
	final String appActivityUrl
	final String pinResetRequestUrl
	final String verifyPinResetUrl
	final String resendPinResetConfirmationCodeUrl
	final String clearPinResetUrl
	final String sslRootCertUrl
	final String appleMobileConfig
	final String sslRootCertCn
	final String password

	User(def json)
	{
		this.creationTime = (json.creationTime) ? YonaServer.parseIsoDateTimeString(json.creationTime) : null
		this.appLastOpenedDate = (json.appLastOpenedDate) ? YonaServer.parseIsoDateString(json.appLastOpenedDate) : null
		this.firstName = json.firstName
		this.lastName = json.lastName
		this.mobileNumber = json.mobileNumber
		this.nickname = json.nickname
		this.userPhotoUrl = json._links?."yona:userPhoto"?.href
		this.editUserPhotoUrl = json._links?."yona:editUserPhoto"?.href
		this.mobileNumberConfirmationUrl = json._links?."yona:confirmMobileNumber"?.href
		this.resendMobileNumberConfirmationCodeUrl = json._links?."yona:resendMobileNumberConfirmationCode"?.href
		this.postOpenAppEventUrl = json._links?."yona:postOpenAppEvent"?.href
		this.hasPrivateData = json.yonaPassword != null
		if (this.hasPrivateData)
		{
			// Private data is available
			this.lastMonitoredActivityDate = (json.lastMonitoredActivityDate) ? YonaServer.parseIsoDateString(json.lastMonitoredActivityDate) : null
			this.password = json.yonaPassword

			this.buddies = (json._embedded?."yona:buddies"?._embedded) ? json._embedded."yona:buddies"._embedded."yona:buddies".collect{new Buddy(it)} : []
			this.vpnProfile = (json.vpnProfile) ? new VPNProfile(json.vpnProfile) : null
		}
		this.goals = (json._embedded?."yona:goals"?._embedded) ? json._embedded."yona:goals"._embedded."yona:goals".collect{Goal.fromJson(it)} : null
		this.devices = (json._embedded?."yona:devices"?._embedded) ? json._embedded."yona:devices"._embedded."yona:devices".collect{new Device(it)} : null
		this.url = json._links.self.href
		this.editUrl = json._links?.edit?.href
		this.buddiesUrl = json._embedded?."yona:buddies"?._links?.self?.href
		this.goalsUrl = json._embedded?."yona:goals"?._links?.self?.href
		this.messagesUrl = json._links?."yona:messages"?.href
		this.dailyActivityReportsUrl = json._links?."yona:dailyActivityReports"?.href
		this.dailyActivityReportsWithBuddiesUrl = json._links?."yona:dailyActivityReportsWithBuddies"?.href
		this.weeklyActivityReportsUrl = json._links?."yona:weeklyActivityReports"?.href
		this.newDeviceRequestUrl = json._links?."yona:newDeviceRequest"?.href
		this.appActivityUrl = json._links?."yona:appActivity"?.href
		this.pinResetRequestUrl = json._links?."yona:requestPinReset"?.href
		this.verifyPinResetUrl = json._links?."yona:verifyPinReset"?.href
		this.resendPinResetConfirmationCodeUrl = json._links?."yona:resendPinResetConfirmationCode"?.href
		this.clearPinResetUrl = json._links?."yona:clearPinReset"?.href
		this.sslRootCertUrl = json._links?."yona:sslRootCert"?.href
		this.appleMobileConfig = json._links?."yona:appleMobileConfig"?.href
		this.sslRootCertCn = json.sslRootCertCN
	}

	def convertToJson()
	{
		def jsonStr = makeUserJsonStringInternal(url, firstName, lastName, password, nickname, mobileNumber, deviceName, deviceOperatingSystem, deviceAppVersion)

		return new JsonSlurper().parseText(jsonStr)
	}

	private static String makeUserJsonStringInternal(url, firstName, lastName, password, nickname, mobileNumber, deviceName = null, deviceOperatingSystem = "UNKNOWN", deviceAppVersion = Device.SUPPORTED_APP_VERSION)
	{
		def devicePropertiesString = (deviceName) ? """"deviceName":"$deviceName", "deviceOperatingSystem":"$deviceOperatingSystem", "deviceAppVersion":"$deviceAppVersion",""" : ""
		def selfLinkString = (url) ? """"self":{"href":"$url"},""" : ""
		def passwordString = (password) ? """"yonaPassword":"${password}",""" : ""
		def json = """{
				"_links":{
					$selfLinkString
				},
				$devicePropertiesString
				"firstName":"${firstName}",
				"lastName":"${lastName}",
				$passwordString
				"nickname":"${nickname}",
				"mobileNumber":"${mobileNumber}"
		}"""
		return json
	}

	def getId()
	{
		getIdFromUrl(url)
	}

	def getRequestingDeviceId()
	{
		YonaServer.getQueryParams(url)["requestingDeviceId"]
	}

	static def getIdFromUrl(def url)
	{
		def queryStringStart = url.indexOf('?')
		if (queryStringStart == -1)
		{
			return url[-36..-1]
		}
		return url[queryStringStart-36..queryStringStart-1]
	}

	def findActiveGoal(def activityCategoryUrl)
	{
		goals.find{ it.activityCategoryUrl == activityCategoryUrl && !it.historyItem }
	}

	static String makeUserJsonString(firstName, lastName, nickname, mobileNumber, deviceName = null, deviceOperatingSystem = "UNKNOWN", deviceAppVersion = Device.SUPPORTED_APP_VERSION)
	{
		makeUserJsonStringInternal(null, firstName, lastName, null, nickname, mobileNumber, deviceName, deviceOperatingSystem, deviceAppVersion)
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
}
