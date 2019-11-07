/*******************************************************************************
 * Copyright (c) 2016, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import nu.yona.server.properties.YonaProperties;
import nu.yona.server.util.HibernateStatisticsService;

@Controller
@RequestMapping(value = "hibernateStatistics", produces = { MediaType.APPLICATION_JSON_VALUE })
public class HibernateStatisticsController extends ControllerBase
{
	@Autowired
	private YonaProperties yonaProperties;

	@Autowired
	private HibernateStatisticsService hibernateStatisticsService;

	@PostMapping(value = "/enable/", params = { "enable" })
	@ResponseBody
	public ResponseEntity<Void> enable(@RequestParam(value = "enable", defaultValue = "false") String enableStr)
	{
		if (!yonaProperties.isEnableHibernateStatsAllowed())
		{
			return createResponse(HttpStatus.NOT_FOUND);
		}

		hibernateStatisticsService.setEnabled(Boolean.TRUE.toString().equals(enableStr));

		return createNoContentResponse();
	}

	@GetMapping(value = "/", params = { "reset" })
	@ResponseBody
	public ResponseEntity<StatisticsResource> getStatistics(
			@RequestParam(value = "reset", defaultValue = "false") String resetStr)
	{
		if (!hibernateStatisticsService.isStatisticsEnabled())
		{
			return createResponse(HttpStatus.NOT_FOUND);
		}

		ResponseEntity<StatisticsResource> responseEntity = createOkResponse(hibernateStatisticsService.getStatistics(),
				createResourceAssembler());
		if (Boolean.TRUE.toString().equals(resetStr))
		{
			hibernateStatisticsService.resetStatistics();
		}
		return responseEntity;
	}

	@PostMapping(value = "/clearCaches/")
	@ResponseBody
	public ResponseEntity<Void> clearCaches()
	{
		if (!hibernateStatisticsService.isStatisticsEnabled())
		{
			return createResponse(HttpStatus.NOT_FOUND);
		}

		hibernateStatisticsService.clearAllUserDataCaches();

		return createNoContentResponse();
	}

	private StatisticsResourceAssembler createResourceAssembler()
	{
		return new StatisticsResourceAssembler();
	}

	public static class StatisticsResource extends EntityModel<HibernateStatisticsService.StatisticsDto>
	{
		public StatisticsResource(HibernateStatisticsService.StatisticsDto statistics)
		{
			super(statistics);
		}
	}

	static class StatisticsResourceAssembler
			extends RepresentationModelAssemblerSupport<HibernateStatisticsService.StatisticsDto, StatisticsResource>
	{
		public StatisticsResourceAssembler()
		{
			super(HibernateStatisticsController.class, StatisticsResource.class);
		}

		@Override
		public StatisticsResource toModel(HibernateStatisticsService.StatisticsDto statistics)
		{
			return instantiateModel(statistics);
		}

		@Override
		protected StatisticsResource instantiateModel(HibernateStatisticsService.StatisticsDto statistics)
		{
			return new StatisticsResource(statistics);
		}
	}
}
