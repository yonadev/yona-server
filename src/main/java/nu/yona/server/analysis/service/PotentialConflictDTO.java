/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class PotentialConflictDTO {
	private UUID accessorID;
	private String category;
	private String url;

	@JsonCreator
	public PotentialConflictDTO(@JsonProperty("accessorID") UUID accessorID,
			@JsonProperty("category") String category, @JsonProperty("url") String url) {
		this.accessorID = accessorID;
		this.category = category;
		this.url = url;
	}

	public UUID getAccessorID() {
		return accessorID;
	}

	public String getCategory() {
		return category;
	}

	public String getURL() {
		return url;
	}
}
