/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.rest;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;

import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.ExposesResourceFor;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.Resources;
import org.springframework.hateoas.mvc.ResourceAssemblerSupport;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import nu.yona.server.goals.rest.GoalController.GoalResource;
import nu.yona.server.goals.service.GoalDTO;
import nu.yona.server.goals.service.GoalService;
import nu.yona.server.rest.RestUtil;

@Controller
@ExposesResourceFor(GoalResource.class)
@RequestMapping(value = "/goal")
public class GoalController {
	@Autowired
	GoalService goalService;

	@RequestMapping(value = "/{id}", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<GoalResource> getGoal(@PathVariable UUID id) {
		return createOKResponse(goalService.getGoal(id));
	}

	@RequestMapping(value = "/", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<Resources<GoalResource>> getAllGoals() {
		return createOKResponse(goalService.getAllGoals());
	}

	@RequestMapping(method = RequestMethod.POST)
	@ResponseBody
	public HttpEntity<GoalResource> addGoal(@RequestBody GoalDTO goal) {
		return createResponse(goalService.addGoal(goal), HttpStatus.CREATED);
	}

	@RequestMapping(value = "/{id}", method = RequestMethod.PUT)
	@ResponseBody
	public HttpEntity<GoalResource> updateGoal(@PathVariable UUID id, @RequestBody GoalDTO goal) {
		return createOKResponse(goalService.updateGoal(id, goal));
	}

	@RequestMapping(value = "/{id}", method = RequestMethod.DELETE)
	@ResponseStatus(HttpStatus.OK)
	public void deleteGoal(@PathVariable UUID id) {
		goalService.deleteGoal(id);
	}

	private HttpEntity<GoalResource> createOKResponse(GoalDTO goal) {
		return createResponse(goal, HttpStatus.OK);
	}

	private HttpEntity<GoalResource> createResponse(GoalDTO goal, HttpStatus status) {
		return new ResponseEntity<GoalResource>(new GoalResourceAssembler().toResource(goal), status);
	}

	private HttpEntity<Resources<GoalResource>> createOKResponse(Set<GoalDTO> goals) {
		return new ResponseEntity<Resources<GoalResource>>(wrapGoalsAsResourceList(goals), HttpStatus.OK);
	}

	private Resources<GoalResource> wrapGoalsAsResourceList(Set<GoalDTO> goals) {
		// TODO: use standard REL and move code to util, consider not passing
		// the REL to the Link constructor
		return new Resources<>(new GoalResourceAssembler().toResources(goals),
				RestUtil.selfLinkWithTrailingSlash(linkTo(GoalController.class)));
	}

	static class GoalResource extends Resource<GoalDTO> {
		public GoalResource(GoalDTO goal) {
			super(goal);
		}
	}

	private static class GoalResourceAssembler extends ResourceAssemblerSupport<GoalDTO, GoalResource> {
		public GoalResourceAssembler() {
			super(GoalController.class, GoalResource.class);
		}

		@Override
		public GoalResource toResource(GoalDTO goal) {
			return super.createResourceWithId(goal.getID(), goal);
		}

		@Override
		protected GoalResource instantiateResource(GoalDTO goal) {
			return new GoalResource(goal);
		}
	}
}
