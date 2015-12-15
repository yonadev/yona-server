package nu.yona.server.test

import groovy.json.*
import nu.yona.server.YonaServer

class Buddy
{
	final String nickname
	final String receivingStatus
	final String sendingStatus
	final User user
	final String url
	Buddy(def json)
	{
		this.nickname = json.nickname
		this.receivingStatus = json.receivingStatus
		this.sendingStatus = json.sendingStatus
		// TODO:  YD-136 - Make the user null when the buddy is removed
		if (json._embedded?.user?.firstName)
		{
			this.user = new User(json._embedded.user)
		}
		this.url = YonaServer.stripQueryString(json._links.self.href)
	}
}
