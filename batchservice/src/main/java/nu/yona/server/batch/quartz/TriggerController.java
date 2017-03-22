package nu.yona.server.batch.quartz;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.ExposesResourceFor;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.Resources;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;
import org.springframework.hateoas.mvc.ResourceAssemblerSupport;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import nu.yona.server.rest.JsonRootRelProvider;

@Controller
@ExposesResourceFor(CronTriggerDto.class)
@RequestMapping(value = "/scheduler/triggers", produces = { MediaType.APPLICATION_JSON_VALUE })
public class TriggerController
{
	@Autowired
	private TriggerManagementService triggerManagementService;

	@RequestMapping(value = "/cron/", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<Resources<TriggerResource>> getAllTriggers()
	{
		return new ResponseEntity<>(createTriggersCollectionResource(triggerManagementService.getAllTriggers(),
				getAllTriggersLinkBuilder().withSelfRel()), HttpStatus.OK);
	}

	@RequestMapping(value = "/cron/{group}/", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<Resources<TriggerResource>> getTriggersInGroup(@PathVariable String group)
	{
		return new ResponseEntity<>(createTriggersCollectionResource(triggerManagementService.getTriggersInGroup(group),
				getTriggersInGroupLinkBuilder(group).withSelfRel()), HttpStatus.OK);
	}

	@RequestMapping(value = "/cron/{group}/", method = RequestMethod.PUT)
	@ResponseBody
	public HttpEntity<Resources<TriggerResource>> updateTriggerGroup(@PathVariable String group,
			@RequestBody Set<CronTriggerDto> triggers)
	{
		return new ResponseEntity<>(createTriggersCollectionResource(triggerManagementService.updateTriggerGroup(group, triggers),
				getTriggersInGroupLinkBuilder(group).withSelfRel()), HttpStatus.OK);
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

	private static Resources<TriggerResource> createTriggersCollectionResource(Set<CronTriggerDto> allTriggers, Link link)
	{
		return new Resources<>(new TriggerResourceAssembler().toResources(allTriggers), link);
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
			addEditLink(triggerResource);/* always editable */
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

		private void addEditLink(Resource<CronTriggerDto> trigger)
		{
			trigger.add(getLinkBuilder(trigger).withRel(JsonRootRelProvider.EDIT_REL));
		}

		private ControllerLinkBuilder getLinkBuilder(Resource<CronTriggerDto> trigger)
		{
			return TriggerController.getTriggerLinkBuilder(trigger.getContent().getGroup(), trigger.getContent().getName());
		}
	}
}
