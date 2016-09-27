/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.rest;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.tools.generic.EscapeTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.velocity.VelocityEngineUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import nu.yona.server.properties.YonaProperties;

@Controller
@RequestMapping(value = "/.well-known/apple-app-site-association", produces = { MediaType.APPLICATION_JSON_VALUE })
public class AppleAppSiteAssociationController
{
	@Autowired
	private YonaProperties yonaProperties;

	@Autowired
	private VelocityEngine velocityEngine;

	@RequestMapping(value = "", method = RequestMethod.GET)
	@ResponseBody
	public ResponseEntity<byte[]> getAppleAppSiteAssociation()
	{
		Map<String, Object> templateParameters = new HashMap<String, Object>();
		templateParameters.put("appleAppID", yonaProperties.getAppleAppID());
		templateParameters.put("esc", new EscapeTool());
		return new ResponseEntity<byte[]>(VelocityEngineUtils
				.mergeTemplateIntoString(velocityEngine, "apple-app-site-association.vm", "UTF-8", templateParameters)
				.getBytes(StandardCharsets.UTF_8), HttpStatus.OK);
	}
}
