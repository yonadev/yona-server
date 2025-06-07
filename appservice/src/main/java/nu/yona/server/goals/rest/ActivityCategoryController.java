/*******************************************************************************
 * Copyright (c) 2015, 2021 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.rest;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import java.util.UUID;

import jakarta.annotation.Nonnull;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.ExposesResourceFor;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

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
	@JsonView(ActivityCategoryDto.AppView.class)
	public HttpEntity<ActivityCategoryResource> getActivityCategory(@PathVariable UUID id)
	{
		return createOkResponse(activityCategoryService.getActivityCategory(id), createResourceAssembler());
	}

	@GetMapping(value = "/")
	@ResponseBody
	@JsonView(ActivityCategoryDto.AppView.class)
	public HttpEntity<CollectionModel<ActivityCategoryResource>> getAllActivityCategories()
	{
		return createOkResponse(activityCategoryService.getAllActivityCategories(), createResourceAssembler(),
				getAllActivityCategoriesLinkBuilder());
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

	public static WebMvcLinkBuilder getActivityCategoryLinkBuilder(UUID id)
	{
		ActivityCategoryController methodOn = methodOn(ActivityCategoryController.class);
		return linkTo(methodOn.getActivityCategory(id));
	}

	public static class ActivityCategoryResource extends EntityModel<ActivityCategoryDto>
	{
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
		public @Nonnull ActivityCategoryResource toModel(@Nonnull ActivityCategoryDto activityCategory)
		{
			return super.createModelWithId(activityCategory.getId(), activityCategory);
		}

		@Override
		protected @Nonnull ActivityCategoryResource instantiateModel(@Nonnull ActivityCategoryDto activityCategory)
		{
			return new ActivityCategoryResource(activityCategory);
		}
	}
}
