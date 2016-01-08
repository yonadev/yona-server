/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.rest;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.ExposesResourceFor;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.Resources;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;
import org.springframework.hateoas.mvc.ResourceAssemblerSupport;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import nu.yona.server.goals.rest.ActivityCategoryController.ActivityCategoryResource;
import nu.yona.server.goals.service.ActivityCategoryDTO;
import nu.yona.server.goals.service.ActivityCategoryService;

@Controller
@ExposesResourceFor(ActivityCategoryResource.class)
@RequestMapping(value = "/activityCategories/")
public class ActivityCategoryController
{
	@Autowired
	private ActivityCategoryService activityCategoryService;

	@RequestMapping(value = "{id}", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<ActivityCategoryResource> getActivityCategory(@PathVariable UUID id)
	{
		return createOKResponse(activityCategoryService.getActivityCategory(id));
	}

	@RequestMapping(method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<Resources<ActivityCategoryResource>> getAllActivityCategories()
	{
		return createOKResponse(activityCategoryService.getAllActivityCategories(), getAllActivityCategoriesLinkBuilder());
	}

	private HttpEntity<ActivityCategoryResource> createOKResponse(ActivityCategoryDTO activityCategory)
	{
		return createResponse(activityCategory, HttpStatus.OK);
	}

	private HttpEntity<ActivityCategoryResource> createResponse(ActivityCategoryDTO activityCategory, HttpStatus status)
	{
		return new ResponseEntity<ActivityCategoryResource>(new ActivityCategoryResourceAssembler().toResource(activityCategory), status);
	}

	private HttpEntity<Resources<ActivityCategoryResource>> createOKResponse(Set<ActivityCategoryDTO> activityCategories,
			ControllerLinkBuilder controllerMethodLinkBuilder)
	{
		return new ResponseEntity<Resources<ActivityCategoryResource>>(
				wrapActivityCategoriesAsResourceList(activityCategories, controllerMethodLinkBuilder), HttpStatus.OK);
	}

	private Resources<ActivityCategoryResource> wrapActivityCategoriesAsResourceList(Set<ActivityCategoryDTO> activityCategories,
			ControllerLinkBuilder controllerMethodLinkBuilder)
	{
		return new Resources<>(new ActivityCategoryResourceAssembler().toResources(activityCategories),
				controllerMethodLinkBuilder.withSelfRel());
	}

	static ControllerLinkBuilder getAllActivityCategoriesLinkBuilder()
	{
		ActivityCategoryController methodOn = methodOn(ActivityCategoryController.class);
		return linkTo(methodOn.getAllActivityCategories());
	}

	public static class ActivityCategoryResource extends Resource<ActivityCategoryDTO>
	{
		public ActivityCategoryResource(ActivityCategoryDTO activityCategory)
		{
			super(activityCategory);
		}
	}

	private static class ActivityCategoryResourceAssembler
			extends ResourceAssemblerSupport<ActivityCategoryDTO, ActivityCategoryResource>
	{
		public ActivityCategoryResourceAssembler()
		{
			super(ActivityCategoryController.class, ActivityCategoryResource.class);
		}

		@Override
		public ActivityCategoryResource toResource(ActivityCategoryDTO activityCategory)
		{
			return super.createResourceWithId(activityCategory.getID(), activityCategory);
		}

		@Override
		protected ActivityCategoryResource instantiateResource(ActivityCategoryDTO activityCategory)
		{
			return new ActivityCategoryResource(activityCategory);
		}
	}
}
