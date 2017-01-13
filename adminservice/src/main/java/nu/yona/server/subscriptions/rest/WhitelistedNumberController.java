/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import nu.yona.server.subscriptions.service.WhitelistedNumberService;

@Controller
@RequestMapping(value = "/whitelistedNumbers")
public class WhitelistedNumberController
{
	@Autowired
	private WhitelistedNumberService whitelistedNumberService;

	@RequestMapping(value = "/", method = RequestMethod.GET)
	public String getIndexPage(Model model)
	{
		model.addAttribute("whitelistedNumbers", whitelistedNumberService.getAllWhitelistedNumbers());

		return "whitelisted-numbers";
	}

	@RequestMapping(value = "/", method = RequestMethod.POST)
	public String addWhitelistedNumber(@RequestParam String mobileNumber, Model model)
	{
		whitelistedNumberService.addWhitelistedNumber(mobileNumber);

		return "redirect:/whitelistedNumbers/";
	}
}
