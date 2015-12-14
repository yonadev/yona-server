package nu.yona.server.test

import nu.yona.server.YonaServer
import groovy.json.*

class User
{
	final String firstName
	final String lastName
	final String mobileNumber
	final String mobileNumberConfirmationUrl
	final boolean hasPrivateData
	final String mobileNumberConfirmationCode
	final String nickname
	final List<String> devices
	final List<String> goals
	final List<Buddy> buddies
	final VPNProfile vpnProfile
	final String url
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
		this.mobileNumberConfirmationUrl = json._links?.confirmMobileNumber?.href
		this.hasPrivateData = hasPrivateData
		this.mobileNumberConfirmationCode = json.confirmationCode
		if (hasPrivateData)
		{
			this.nickname = json.nickname
			
			this.buddies = (json._embedded?.buddies) ? json._embedded.buddies.collect{new Buddy(it)} : []
			this.devices = json.devices.collect{"$it"}
			this.goals = json.goals.collect{"$it"}
			this.vpnProfile = (json.vpnProfile) ? new VPNProfile(json.vpnProfile) : null
		}
		this.url = YonaServer.stripQueryString(json._links.self.href)
	}

	def convertToJSON()
	{
		def jsonStr = makeUserJsonStringInternal(url, firstName, lastName, nickname, mobileNumber, devices, goals, [])

		return new JsonSlurper().parseText(jsonStr)
	}

	private static String makeUserJsonStringInternal(url, firstName, lastName, nickname, mobileNumber, devices, goals, buddies)
	{
		def selfLinkString = (url) ? """"_links":{"self":{"href":"$url"}},""" : ""
		def devicesString = YonaServer.makeStringList(devices)
		def goalsString = YonaServer.makeStringList(goals)
		def json = """{
				$selfLinkString
				"firstName":"${firstName}",
				"lastName":"${lastName}",
				"nickname":"${nickname}",
				"mobileNumber":"${mobileNumber}",
				"devices":[
					${devicesString}
				],
				"goals":[
					${goalsString}
				]
		}"""
		return json
	}

	static String makeUserJsonString(firstName, lastName, nickname, mobileNumber, devices, goals, buddies)
	{
		makeUserJsonStringInternal(null, firstName, lastName, nickname, mobileNumber, devices, goals, buddies)
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
