/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.rest;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import nu.yona.server.exceptions.YonaException;
import nu.yona.server.subscriptions.service.WhitelistedNumberService;

@Controller
@RequestMapping(value = "/whitelistedNumbers", produces = { MediaType.TEXT_HTML_VALUE })
public class WhitelistedNumberController
{
	@Autowired
	private WhitelistedNumberService whitelistedNumberService;

	@Autowired
	private VelocityEngine velocityEngine;

	@RequestMapping(value = "/", method = RequestMethod.GET)
	@ResponseBody
	public ResponseEntity<byte[]> getIndexPage()
	{
		VelocityContext velocityContext = new VelocityContext();
		velocityContext.put("whitelistedNumbers", whitelistedNumberService.getAllWhitelistedNumbers());

		try (StringWriter stringWriter = new StringWriter())
		{
			velocityEngine.mergeTemplate("whitelisted-numbers.vm", "UTF-8", velocityContext, stringWriter);
			return new ResponseEntity<byte[]>(stringWriter.toString().getBytes(StandardCharsets.UTF_8), HttpStatus.OK);
		}
		catch (IOException e)
		{
			throw YonaException.unexpected(e);
		}
	}

	@RequestMapping(value = "/", method = RequestMethod.POST)
	public ResponseEntity<byte[]> addWhitelistedNumber(@RequestParam String mobileNumber)
	{
		whitelistedNumberService.addWhitelistedNumber(mobileNumber);

		return getIndexPage();
	}
}
