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

import nu.yona.server.subscriptions.service.WhiteListedNumberService;

@Controller
@RequestMapping(value = "/whiteListedNumbers")
public class WhiteListedNumberController
{
	@Autowired
	private WhiteListedNumberService whiteListedNumberService;

	@RequestMapping(value = "/", method = RequestMethod.GET)
	public String getIndexPage(Model model)
	{
		model.addAttribute("whiteListedNumbers", whiteListedNumberService.getAllWhiteListedNumbers());

		return "white-listed-numbers";
	}

	@RequestMapping(value = "/", method = RequestMethod.POST)
	public String addWhiteListedNumber(@RequestParam String mobileNumber)
	{
		whiteListedNumberService.addWhiteListedNumber(mobileNumber);

		return "redirect:/whiteListedNumbers/";
	}
}
