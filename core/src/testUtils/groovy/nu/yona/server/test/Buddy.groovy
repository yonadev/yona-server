/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.test

import groovy.json.*
import nu.yona.server.YonaServer

class Buddy
{
	final String nickname
	final String receivingStatus
	final String sendingStatus
	final User user
	final List<Goal> goals
	final String url
	final String editURL
	Buddy(def json)
	{
		this.nickname = json.nickname
		this.receivingStatus = json.receivingStatus
		this.sendingStatus = json.sendingStatus
		// TODO:  YD-136 - Make the user null when the buddy is removed
		if (json._embedded?."yona:user"?.firstName)
		{
			this.user = new User(json._embedded."yona:user")
		}
		this.goals = (json._embedded?."yona:goals"?._embedded?."yona:goals") ? json._embedded."yona:goals"._embedded."yona:goals".collect{Goal.fromJSON(it)} : null
		this.url = YonaServer.stripQueryString(json._links.self.href)
		this.editURL = json._links?.edit?.href
	}
}