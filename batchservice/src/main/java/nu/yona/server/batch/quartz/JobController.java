/*******************************************************************************
 * Copyright (c) 2017 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
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

@Controller
@ExposesResourceFor(JobDto.class)
@RequestMapping(value = "/scheduler/jobs", produces = { MediaType.APPLICATION_JSON_VALUE })
public class JobController
{
	@Autowired
	private JobManagementService jobManagementService;

	@RequestMapping(value = "/", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<Resources<JobResource>> getAllJobs()
	{
		return new ResponseEntity<>(
				createJobsCollectionResource(jobManagementService.getAllJobs(), getAllJobsLinkBuilder().withSelfRel()),
				HttpStatus.OK);
	}

	@RequestMapping(value = "/{group}/", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<Resources<JobResource>> getJobsInGroup(@PathVariable String group)
	{
		return new ResponseEntity<>(createJobsCollectionResource(jobManagementService.getJobsInGroup(group),
				getJobsInGroupLinkBuilder(group).withSelfRel()), HttpStatus.OK);
	}

	@RequestMapping(value = "/{group}/", method = RequestMethod.PUT)
	@ResponseBody
	public HttpEntity<Resources<JobResource>> updateJobGroup(@PathVariable String group, @RequestBody Set<JobDto> jobs)
	{
		return new ResponseEntity<>(createJobsCollectionResource(jobManagementService.updateJobGroup(group, jobs),
				getJobsInGroupLinkBuilder(group).withSelfRel()), HttpStatus.OK);
	}

	@RequestMapping(value = "/{group}/{name}", method = RequestMethod.GET)
	@ResponseBody
	@ResponseStatus(HttpStatus.OK)
	public JobResource getJob(@PathVariable String group, @PathVariable String name)
	{
		return new JobResourceAssembler().toResource(jobManagementService.getJob(name, group));
	}

	@RequestMapping(value = "/{group}/", method = RequestMethod.POST)
	@ResponseBody
	@ResponseStatus(HttpStatus.CREATED)
	public JobResource addJob(@PathVariable String group, @RequestBody JobDto job)
	{
		job.setGroup(group);
		return new JobResourceAssembler().toResource(jobManagementService.addJob(group, job));
	}

	private static Resources<JobResource> createJobsCollectionResource(Set<JobDto> allJobs, Link link)
	{
		return new Resources<>(new JobResourceAssembler().toResources(allJobs), link);
	}

	private static ControllerLinkBuilder getAllJobsLinkBuilder()
	{
		JobController methodOn = methodOn(JobController.class);
		return linkTo(methodOn.getAllJobs());
	}

	private static ControllerLinkBuilder getJobsInGroupLinkBuilder(String group)
	{
		JobController methodOn = methodOn(JobController.class);
		return linkTo(methodOn.getJobsInGroup(group));
	}

	static ControllerLinkBuilder getJobLinkBuilder(String group, String name)
	{
		JobController methodOn = methodOn(JobController.class);
		return linkTo(methodOn.getJob(group, name));
	}

	static class JobResource extends Resource<JobDto>
	{
		public JobResource(JobDto job)
		{
			super(job);
		}
	}

	public static class JobResourceAssembler extends ResourceAssemblerSupport<JobDto, JobResource>
	{
		public JobResourceAssembler()
		{
			super(JobController.class, JobResource.class);
		}

		@Override
		public JobResource toResource(JobDto job)
		{
			JobResource jobResource = instantiateResource(job);
			addSelfLink(jobResource);
			return jobResource;
		}

		@Override
		protected JobResource instantiateResource(JobDto job)
		{
			return new JobResource(job);
		}

		private void addSelfLink(Resource<JobDto> job)
		{
			job.add(getLinkBuilder(job).withSelfRel());
		}

		private ControllerLinkBuilder getLinkBuilder(Resource<JobDto> job)
		{
			return JobController.getJobLinkBuilder(job.getContent().getGroup(), job.getContent().getName());
		}
	}

}
