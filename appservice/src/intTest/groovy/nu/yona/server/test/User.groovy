package nu.yona.server.test

import nu.yona.server.YonaServer
import groovy.json.*

class User
{
	final String firstName
	final String lastName
	final String mobileNumber
	final boolean hasPrivateData
	final String mobileNumberConfirmationCode
	final String nickname
	final VPNProfile vpnProfile
	final String url
	final String password
	User(def json, String password)
	{
		this(json, true)
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
		this.hasPrivateData = hasPrivateData
		this.mobileNumberConfirmationCode = json.confirmationCode
		if (hasPrivateData)
		{
			this.nickname = json.nickname
			this.vpnProfile = new VPNProfile(json.vpnProfile)
		}
		this.url = YonaServer.stripQueryString(json._links.self.href)
	}
}

class VPNProfile
{
	final String vpnLoginID

	VPNProfile(def json)
	{
		this.vpnLoginID = json.vpnLoginID
	}
}
