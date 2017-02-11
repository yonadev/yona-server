/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.rest;

import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import nu.yona.server.properties.YonaProperties;
import nu.yona.server.util.ThymeleafUtil;

@Controller
@RequestMapping(value = "/.well-known/apple-app-site-association", produces = { MediaType.APPLICATION_JSON_VALUE })
public class AppleAppSiteAssociationController
{
	@Autowired
	private YonaProperties yonaProperties;

	@Autowired
	@Qualifier("otherTemplateEngine")
	private TemplateEngine templateEngine;

	@RequestMapping(value = "", method = RequestMethod.GET)
	@ResponseBody
	public ResponseEntity<byte[]> getAppleAppSiteAssociation()
	{
		Context ctx = ThymeleafUtil.createContext();
		ctx.setVariable("appleAppId", yonaProperties.getAppleAppId());

		return new ResponseEntity<>(
				templateEngine.process("apple-app-site-association.json", ctx).getBytes(StandardCharsets.UTF_8), HttpStatus.OK);
	}
}
