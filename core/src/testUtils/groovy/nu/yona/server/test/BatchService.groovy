/*******************************************************************************
 * Copyright (c) 2017 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.test

class BatchService extends Service
{
	final ACTIVITY_AGGREGATION_TRIGGER_PATH = "/batch/aggregateActivities/"

	BatchService()
	{
		super("yona.batchservice.url", "http://localhost:8083")
	}


	def triggerActivityAggregationBatchJob()
	{
		yonaServer.postJson(ACTIVITY_AGGREGATION_TRIGGER_PATH, "{}")
	}
}
