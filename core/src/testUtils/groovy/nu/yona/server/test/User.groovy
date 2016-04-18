/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.test

import groovy.json.*
import nu.yona.server.YonaServer

class User
{
	final String firstName
	final String lastName
	final String mobileNumber
	final String mobileNumberConfirmationUrl
	final String resendMobileNumberConfirmationCodeUrl
	final boolean hasPrivateData
	final String nickname
	final List<Goal> goals
	final List<Buddy> buddies
	final VPNProfile vpnProfile
	final String url
	final String editURL
	final String buddiesUrl
	final String goalsUrl
	final String messagesUrl
	final String dailyActivityReportsUrl
	final String weeklyActivityReportsUrl
	final String newDeviceRequestUrl
	final String appActivityUrl
	final String pinResetRequestUrl
	final String verifyPinResetUrl
	final String clearPinResetUrl
	final String password

	User(def json, String password)
	{
		this(json, true)
		this.password = password
	}
	User(def json, String password, boolean hasPrivateData)
	{
		this(json, hasPrivateData)
		this.password = password
	}
	User(def json)
	{
		this(json, false)
	}
	private User(def json, boolean hasPrivateData)
	{
		this.firstName = json.firstName
		this.lastName = json.lastName
		this.mobileNumber = json.mobileNumber
		this.mobileNumberConfirmationUrl = json._links?."yona:confirmMobileNumber"?.href
		this.resendMobileNumberConfirmationCodeUrl = json._links?."yona:resendMobileNumberConfirmationCode"?.href
		this.hasPrivateData = hasPrivateData
		if (hasPrivateData)
		{
			this.nickname = json.nickname

			this.buddies = (json._embedded?."yona:buddies"?._embedded) ? json._embedded."yona:buddies"._embedded."yona:buddies".collect{new Buddy(it)} : []
			this.goals = (json._embedded?."yona:goals"?._embedded) ? json._embedded."yona:goals"._embedded."yona:goals".collect{Goal.fromJSON(it)} : []
			this.vpnProfile = (json.vpnProfile) ? new VPNProfile(json.vpnProfile) : null
		}
		this.url = YonaServer.stripQueryString(json._links.self.href)
		this.editURL = json._links?.edit?.href
		this.buddiesUrl = json._embedded?."yona:buddies"?._links?.self?.href
		this.goalsUrl = json._embedded?."yona:goals"?._links?.self?.href
		this.messagesUrl = json._links?."yona:messages"?.href
		this.dailyActivityReportsUrl = json._links?."yona:dailyActivityReports"?.href
		this.weeklyActivityReportsUrl = json._links?."yona:weeklyActivityReports"?.href
		this.newDeviceRequestUrl = json._links?."yona:newDeviceRequest"?.href
		this.appActivityUrl = json._links?."yona:appActivity"?.href
		this.pinResetRequestUrl = json._links?."yona:requestPinReset"?.href
		this.verifyPinResetUrl = json._links?."yona:verifyPinReset"?.href
		this.clearPinResetUrl = json._links?."yona:clearPinReset"?.href
	}

	def convertToJSON()
	{
		def jsonStr = makeUserJsonStringInternal(url, firstName, lastName, nickname, mobileNumber)

		return new JsonSlurper().parseText(jsonStr)
	}

	private static String makeUserJsonStringInternal(url, firstName, lastName, nickname, mobileNumber)
	{
		def selfLinkString = (url) ? """"_links":{"self":{"href":"$url"}},""" : ""
		def json = """{
				$selfLinkString
				"firstName":"${firstName}",
				"lastName":"${lastName}",
				"nickname":"${nickname}",
				"mobileNumber":"${mobileNumber}"
		}"""
		return json
	}

	static String makeUserJsonString(firstName, lastName, nickname, mobileNumber)
	{
		makeUserJsonStringInternal(null, firstName, lastName, nickname, mobileNumber)
	}
}

class VPNProfile
{
	final String vpnLoginID
	final String vpnPassword
	final String openVPNProfile

	VPNProfile(def json)
	{
		this.vpnLoginID = json.vpnLoginID
		this.vpnPassword = json.vpnPassword
		this.openVPNProfile = json.openVPNProfile
	}
}
