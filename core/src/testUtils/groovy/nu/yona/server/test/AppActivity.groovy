/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.test

import groovy.json.*
import nu.yona.server.YonaServer

class AppActivity
{
	final def application
	final Date startTime
	final Date endTime
	AppActivity(application, Date startTime, Date endTime)
	{
		this.application = application
		this.startTime = startTime
		this.endTime = endTime
	}

	String getJson()
	{
		def startTimeString = YonaServer.toIsoDateString(startTime)
		def endTimeString = YonaServer.toIsoDateString(endTime)
		"""{
			"application":"$application",
			"startTime":"$startTimeString",
			"endTime":"$endTimeString"
		}"""
	}
}