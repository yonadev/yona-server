/*******************************************************************************
 * Copyright (c) 2016, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.rest;

import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import nu.yona.server.properties.YonaProperties;
import nu.yona.server.util.ThymeleafUtil;

@Controller
@RequestMapping(value = "", produces = { MediaType.APPLICATION_JSON_VALUE })
public class StandardResourcesController
{
	@Autowired
	private YonaProperties yonaProperties;

	@Autowired
	@Qualifier("otherTemplateEngine")
	private TemplateEngine templateEngine;

	@GetMapping(value = "/.well-known/apple-app-site-association")
	@ResponseBody
	public ResponseEntity<byte[]> getAppleAppSiteAssociation()
	{
		Context ctx = ThymeleafUtil.createContext();
		ctx.setVariable("appleAppId", yonaProperties.getAppleAppId());

		return new ResponseEntity<>(
				templateEngine.process("apple-app-site-association.json", ctx).getBytes(StandardCharsets.UTF_8), HttpStatus.OK);
	}

	@GetMapping(value = "/ssl/rootcert.cer", produces = { "application/pkix-cert" })
	@ResponseBody
	public FileSystemResource getSslRootCert()
	{
		return new FileSystemResource(yonaProperties.getSecurity().getSslRootCertFile());
	}

	@GetMapping(value = "/vpn/profile.ovpn", produces = { "application/x-openvpn-profile" })
	@ResponseBody
	public FileSystemResource getOvpnProfile()
	{
		return new FileSystemResource(yonaProperties.getSecurity().getOvpnProfileFile());
	}
}
