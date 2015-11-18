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

import nu.yona.server.goals.rest.GoalController.GoalResource;
import nu.yona.server.goals.service.GoalDTO;
import nu.yona.server.goals.service.GoalService;

@Controller
@ExposesResourceFor(GoalResource.class)
@RequestMapping(value = "/goals/")
public class GoalController
{
    @Autowired
    private GoalService goalService;

    @RequestMapping(value = "{id}", method = RequestMethod.GET)
    @ResponseBody
    public HttpEntity<GoalResource> getGoal(@PathVariable UUID id)
    {
        return createOKResponse(goalService.getGoal(id));
    }

    @RequestMapping(method = RequestMethod.GET)
    @ResponseBody
    public HttpEntity<Resources<GoalResource>> getAllGoals()
    {
        return createOKResponse(goalService.getAllGoals(), getAllGoalsLinkBuilder());
    }

    private HttpEntity<GoalResource> createOKResponse(GoalDTO goal)
    {
        return createResponse(goal, HttpStatus.OK);
    }

    private HttpEntity<GoalResource> createResponse(GoalDTO goal, HttpStatus status)
    {
        return new ResponseEntity<GoalResource>(new GoalResourceAssembler().toResource(goal), status);
    }

    private HttpEntity<Resources<GoalResource>> createOKResponse(Set<GoalDTO> goals,
            ControllerLinkBuilder controllerMethodLinkBuilder)
    {
        return new ResponseEntity<Resources<GoalResource>>(wrapGoalsAsResourceList(goals, controllerMethodLinkBuilder),
                HttpStatus.OK);
    }

    private Resources<GoalResource> wrapGoalsAsResourceList(Set<GoalDTO> goals, ControllerLinkBuilder controllerMethodLinkBuilder)
    {
        return new Resources<>(new GoalResourceAssembler().toResources(goals), controllerMethodLinkBuilder.withSelfRel());
    }

    static ControllerLinkBuilder getAllGoalsLinkBuilder()
    {
        GoalController methodOn = methodOn(GoalController.class);
        return linkTo(methodOn.getAllGoals());
    }

    public static class GoalResource extends Resource<GoalDTO>
    {
        public GoalResource(GoalDTO goal)
        {
            super(goal);
        }
    }

    private static class GoalResourceAssembler extends ResourceAssemblerSupport<GoalDTO, GoalResource>
    {
        public GoalResourceAssembler()
        {
            super(GoalController.class, GoalResource.class);
        }

        @Override
        public GoalResource toResource(GoalDTO goal)
        {
            return super.createResourceWithId(goal.getID(), goal);
        }

        @Override
        protected GoalResource instantiateResource(GoalDTO goal)
        {
            return new GoalResource(goal);
        }
    }
}
