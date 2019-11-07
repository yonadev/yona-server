/*******************************************************************************
 * Copyright (c) 2017, 2019 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.batch.quartz;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.ExposesResourceFor;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

	@GetMapping(value = "/cron/")
	@ResponseBody
	public HttpEntity<CollectionModel<TriggerResource>> getAllTriggers()
	{
		return createOkResponse(triggerManagementService.getAllTriggers(), createResourceAssembler(),
				getAllTriggersLinkBuilder());
	}

	@GetMapping(value = "/cron/{group}/")
	@ResponseBody
	public HttpEntity<CollectionModel<TriggerResource>> getTriggersInGroup(@PathVariable String group)
	{
		return createOkResponse(triggerManagementService.getTriggersInGroup(group), createResourceAssembler(),
				getTriggersInGroupLinkBuilder(group));
	}

	@PutMapping(value = "/cron/{group}/")
	@ResponseBody
	public HttpEntity<CollectionModel<TriggerResource>> updateTriggerGroup(@PathVariable String group,
			@RequestBody Set<CronTriggerDto> triggers)
	{
		return createOkResponse(triggerManagementService.updateTriggerGroup(group, triggers), createResourceAssembler(),
				getTriggersInGroupLinkBuilder(group));
	}

	@GetMapping(value = "/cron/{group}/{name}")
	@ResponseBody
	@ResponseStatus(HttpStatus.OK)
	public TriggerResource getTrigger(@PathVariable String group, @PathVariable String name)
	{
		return new TriggerResourceAssembler().toModel(triggerManagementService.getTrigger(name, group));
	}

	@PostMapping(value = "/cron/{group}/")
	@ResponseBody
	@ResponseStatus(HttpStatus.CREATED)
	public TriggerResource addTrigger(@PathVariable String group, @RequestBody CronTriggerDto trigger)
	{
		trigger.setGroup(group);
		return new TriggerResourceAssembler().toModel(triggerManagementService.addTrigger(group, trigger));
	}

	private TriggerResourceAssembler createResourceAssembler()
	{
		return new TriggerResourceAssembler();
	}

	private static WebMvcLinkBuilder getAllTriggersLinkBuilder()
	{
		TriggerController methodOn = methodOn(TriggerController.class);
		return linkTo(methodOn.getAllTriggers());
	}

	private static WebMvcLinkBuilder getTriggersInGroupLinkBuilder(String group)
	{
		TriggerController methodOn = methodOn(TriggerController.class);
		return linkTo(methodOn.getTriggersInGroup(group));
	}

	static WebMvcLinkBuilder getTriggerLinkBuilder(String group, String name)
	{
		TriggerController methodOn = methodOn(TriggerController.class);
		return linkTo(methodOn.getTrigger(group, name));
	}

	static class TriggerResource extends EntityModel<CronTriggerDto>
	{
		public TriggerResource(CronTriggerDto trigger)
		{
			super(trigger);
		}
	}

	public static class TriggerResourceAssembler extends RepresentationModelAssemblerSupport<CronTriggerDto, TriggerResource>
	{
		public TriggerResourceAssembler()
		{
			super(TriggerController.class, TriggerResource.class);
		}

		@Override
		public TriggerResource toModel(CronTriggerDto trigger)
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

		private void addSelfLink(EntityModel<CronTriggerDto> trigger)
		{
			trigger.add(getLinkBuilder(trigger).withSelfRel());
		}

		private WebMvcLinkBuilder getLinkBuilder(EntityModel<CronTriggerDto> trigger)
		{
			return TriggerController.getTriggerLinkBuilder(trigger.getContent().getGroup(), trigger.getContent().getName());
		}
	}
}
