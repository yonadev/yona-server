/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/*
 * Online activity detected by Smoothwall.
 * @see AnalysisEngineService
 * @see AppActivityDTO
 */
@JsonRootName("networkActivity")
public class NetworkActivityDTO
{
	private UUID vpnLoginID;
	private Set<String> categories;
	private String url;

	@JsonCreator
	public NetworkActivityDTO(@JsonProperty("vpnLoginID") UUID vpnLoginID,
			@JsonProperty("categories") @JsonDeserialize(as = TreeSet.class, contentAs = String.class) Set<String> categories,
			@JsonProperty("url") String url)
	{
		this.vpnLoginID = vpnLoginID;
		this.categories = categories;
		this.url = url;
	}

	public UUID getVPNLoginID()
	{
		return vpnLoginID;
	}

	public Set<String> getCategories()
	{
		return Collections.unmodifiableSet(categories);
	}

	public String getURL()
	{
		return url;
	}
}
