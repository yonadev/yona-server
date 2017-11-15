/*******************************************************************************
 * Copyright (c) 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.batch.quartz;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.ExposesResourceFor;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.Resources;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;
import org.springframework.hateoas.mvc.ResourceAssemblerSupport;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import nu.yona.server.rest.ControllerBase;

@Controller
@ExposesResourceFor(CronTriggerDto.class)
@RequestMapping(value = "/scheduler/triggers", produces = { MediaType.APPLICATION_JSON_VALUE })
public class TriggerController extends ControllerBase
{
	@Autowired
	private TriggerManagementService triggerManagementService;

	@RequestMapping(value = "/cron/", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<Resources<TriggerResource>> getAllTriggers()
	{
		return createOkResponse(triggerManagementService.getAllTriggers(), createResourceAssembler(),
				getAllTriggersLinkBuilder());
	}

	@RequestMapping(value = "/cron/{group}/", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<Resources<TriggerResource>> getTriggersInGroup(@PathVariable String group)
	{
		return createOkResponse(triggerManagementService.getTriggersInGroup(group), createResourceAssembler(),
				getTriggersInGroupLinkBuilder(group));
	}

	@RequestMapping(value = "/cron/{group}/", method = RequestMethod.PUT)
	@ResponseBody
	public HttpEntity<Resources<TriggerResource>> updateTriggerGroup(@PathVariable String group,
			@RequestBody Set<CronTriggerDto> triggers)
	{
		return createOkResponse(triggerManagementService.updateTriggerGroup(group, triggers), createResourceAssembler(),
				getTriggersInGroupLinkBuilder(group));
	}

	@RequestMapping(value = "/cron/{group}/{name}", method = RequestMethod.GET)
	@ResponseBody
	@ResponseStatus(HttpStatus.OK)
	public TriggerResource getTrigger(@PathVariable String group, @PathVariable String name)
	{
		return new TriggerResourceAssembler().toResource(triggerManagementService.getTrigger(name, group));
	}

	@RequestMapping(value = "/cron/{group}/", method = RequestMethod.POST)
	@ResponseBody
	@ResponseStatus(HttpStatus.CREATED)
	public TriggerResource addTrigger(@PathVariable String group, @RequestBody CronTriggerDto trigger)
	{
		trigger.setGroup(group);
		return new TriggerResourceAssembler().toResource(triggerManagementService.addTrigger(group, trigger));
	}

	private TriggerResourceAssembler createResourceAssembler()
	{
		return new TriggerResourceAssembler();
	}

	private static ControllerLinkBuilder getAllTriggersLinkBuilder()
	{
		TriggerController methodOn = methodOn(TriggerController.class);
		return linkTo(methodOn.getAllTriggers());
	}

	private static ControllerLinkBuilder getTriggersInGroupLinkBuilder(String group)
	{
		TriggerController methodOn = methodOn(TriggerController.class);
		return linkTo(methodOn.getTriggersInGroup(group));
	}

	static ControllerLinkBuilder getTriggerLinkBuilder(String group, String name)
	{
		TriggerController methodOn = methodOn(TriggerController.class);
		return linkTo(methodOn.getTrigger(group, name));
	}

	static class TriggerResource extends Resource<CronTriggerDto>
	{
		public TriggerResource(CronTriggerDto trigger)
		{
			super(trigger);
		}
	}

	public static class TriggerResourceAssembler extends ResourceAssemblerSupport<CronTriggerDto, TriggerResource>
	{
		public TriggerResourceAssembler()
		{
			super(TriggerController.class, TriggerResource.class);
		}

		@Override
		public TriggerResource toResource(CronTriggerDto trigger)
		{
			TriggerResource triggerResource = instantiateResource(trigger);
			addSelfLink(triggerResource);
			return triggerResource;
		}

		@Override
		protected TriggerResource instantiateResource(CronTriggerDto trigger)
		{
			return new TriggerResource(trigger);
		}

		private void addSelfLink(Resource<CronTriggerDto> trigger)
		{
			trigger.add(getLinkBuilder(trigger).withSelfRel());
		}

		private ControllerLinkBuilder getLinkBuilder(Resource<CronTriggerDto> trigger)
		{
			return TriggerController.getTriggerLinkBuilder(trigger.getContent().getGroup(), trigger.getContent().getName());
		}
	}
}
