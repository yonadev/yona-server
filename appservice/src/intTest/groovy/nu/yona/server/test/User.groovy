package nu.yona.server.test

import nu.yona.server.YonaServer
import groovy.json.*

class User
{
	final String firstName
	final String lastName
	final String mobileNumber
	final boolean mobileNumberConfirmed
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
		this.mobileNumberConfirmed = json.mobileNumberConfirmed // TODO: Replace this with the (optional) URL YD-126
		this.hasPrivateData = hasPrivateData
		this.mobileNumberConfirmationCode = json.confirmationCode
		if (hasPrivateData)
		{
			this.nickname = json.nickname
			this.buddies = json._embedded.buddies.collect{new Buddy(it)}
			this.devices = json.devices.collect{"$it"}
			this.goals = json.goals.collect{"$it"}
			this.vpnProfile = new VPNProfile(json.vpnProfile)
		}
		this.url = YonaServer.stripQueryString(json._links.self.href)
	}

	def convertToJSON()
	{
		return new JsonSlurper().parseText(new JsonBuilder(this).toPrettyString())
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
