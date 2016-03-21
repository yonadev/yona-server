/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import nu.yona.server.analysis.service.AnalysisEngineService;
import nu.yona.server.analysis.service.NetworkActivityDTO;

@Controller
@RequestMapping(value = "/analysisEngine")
public class AnalysisEngineController
{
	@Autowired
	private AnalysisEngineService analysisEngineService;

	@RequestMapping(value = "/", method = RequestMethod.POST)
	@ResponseStatus(value = HttpStatus.OK)
	public void analyze(@RequestBody NetworkActivityDTO potentialConflictPayload)
	{
		analysisEngineService.analyze(potentialConflictPayload);
	}

	@RequestMapping(value = "/relevantSmoothwallCategories/", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<CategoriesResource> getRelevantSmoothwallCategories()
	{
		CategoriesDTO categories = new CategoriesDTO(analysisEngineService.getRelevantSmoothwallCategories());
		return new ResponseEntity<CategoriesResource>(new CategoriesResource(categories), HttpStatus.OK);
	}

	public static class CategoriesResource extends Resource<CategoriesDTO>
	{
		public CategoriesResource(CategoriesDTO categories)
		{
			super(categories);
		}
	}
}
