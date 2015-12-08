package nu.yona.server.test

import nu.yona.server.YonaServer
import groovy.json.*

class Buddy
{
	final String nickname
	final User user;
	final String url
	Buddy(def json)
	{
		this.nickname = json.nickname
		// TODO: Is it appropriate to return the user with all null data? I'd rather omit the user in that case.
		if (json.receivingStatus != "REJECTED" || json.sendingStatus != "REJECTED")
		{
			this.user = new User(json._embedded.user)
		}
		this.url = YonaServer.stripQueryString(json._links.self.href)
	}
}
