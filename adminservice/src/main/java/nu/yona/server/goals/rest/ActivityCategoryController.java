/*******************************************************************************
 * Copyright (c) 2015, 2021 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.rest;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.ExposesResourceFor;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.fasterxml.jackson.annotation.JsonView;

import nu.yona.server.goals.rest.ActivityCategoryController.ActivityCategoryResource;
import nu.yona.server.goals.service.ActivityCategoryDto;
import nu.yona.server.goals.service.ActivityCategoryService;
import nu.yona.server.rest.ControllerBase;

@Controller
@ExposesResourceFor(ActivityCategoryResource.class)
@RequestMapping(value = "/activityCategories", produces = { MediaType.APPLICATION_JSON_VALUE })
public class ActivityCategoryController extends ControllerBase
{
	@Autowired
	private ActivityCategoryService activityCategoryService;

	@GetMapping(value = "/{id}")
	@ResponseBody
	@JsonView(ActivityCategoryDto.AdminView.class)
	public HttpEntity<ActivityCategoryResource> getActivityCategory(@PathVariable UUID id)
	{
		return createOkResponse(activityCategoryService.getActivityCategory(id), createResourceAssembler());
	}

	@GetMapping(value = "/")
	@ResponseBody
	@JsonView(ActivityCategoryDto.AdminView.class)
	public HttpEntity<CollectionModel<ActivityCategoryResource>> getAllActivityCategories()
	{
		return createOkResponse(activityCategoryService.getAllActivityCategories(), createResourceAssembler(),
				getAllActivityCategoriesLinkBuilder());
	}

	@PostMapping(value = "/")
	@ResponseBody
	@JsonView(ActivityCategoryDto.AdminView.class)
	public HttpEntity<ActivityCategoryResource> addActivityCategory(@RequestBody ActivityCategoryDto activityCategory)
	{
		activityCategory.setId(UUID.randomUUID());
		return createOkResponse(activityCategoryService.addActivityCategory(activityCategory), createResourceAssembler());
	}

	@PutMapping(value = "/{id}")
	@ResponseBody
	@JsonView(ActivityCategoryDto.AdminView.class)
	public HttpEntity<ActivityCategoryResource> updateActivityCategory(@PathVariable UUID id,
			@RequestBody ActivityCategoryDto activityCategory)
	{
		return createOkResponse(activityCategoryService.updateActivityCategory(id, activityCategory), createResourceAssembler());
	}

	@PutMapping(value = "/")
	@ResponseBody
	@JsonView(ActivityCategoryDto.AdminView.class)
	public HttpEntity<CollectionModel<ActivityCategoryResource>> updateActivityCategorySet(
			@RequestBody Set<ActivityCategoryDto> activityCategorySet)
	{
		activityCategoryService.updateActivityCategorySet(activityCategorySet);
		return createOkResponse(activityCategoryService.getAllActivityCategories(), createResourceAssembler(),
				getAllActivityCategoriesLinkBuilder());
	}

	@DeleteMapping(value = "/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteActivityCategory(@PathVariable UUID id)
	{
		activityCategoryService.deleteActivityCategory(id);
	}

	private ActivityCategoryResourceAssembler createResourceAssembler()
	{
		return new ActivityCategoryResourceAssembler();
	}

	static WebMvcLinkBuilder getAllActivityCategoriesLinkBuilder()
	{
		ActivityCategoryController methodOn = methodOn(ActivityCategoryController.class);
		return linkTo(methodOn.getAllActivityCategories());
	}

	public static class ActivityCategoryResource extends EntityModel<ActivityCategoryDto>
	{
		@SuppressWarnings("deprecation") // Constructor will become protected, see spring-projects/spring-hateoas#1297
		public ActivityCategoryResource(ActivityCategoryDto activityCategory)
		{
			super(activityCategory);
		}
	}

	private static class ActivityCategoryResourceAssembler
			extends RepresentationModelAssemblerSupport<ActivityCategoryDto, ActivityCategoryResource>
	{
		public ActivityCategoryResourceAssembler()
		{
			super(ActivityCategoryController.class, ActivityCategoryResource.class);
		}

		@Override
		public ActivityCategoryResource toModel(ActivityCategoryDto activityCategory)
		{
			return super.createModelWithId(activityCategory.getId(), activityCategory);
		}

		@Override
		protected ActivityCategoryResource instantiateModel(ActivityCategoryDto activityCategory)
		{
			return new ActivityCategoryResource(activityCategory);
		}
	}
}
