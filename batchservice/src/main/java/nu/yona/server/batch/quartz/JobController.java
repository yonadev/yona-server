/*******************************************************************************
 * Copyright (c) 2017, 2021 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.batch.quartz;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import java.util.Set;

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
@ExposesResourceFor(JobDto.class)
@RequestMapping(value = "/scheduler/jobs", produces = { MediaType.APPLICATION_JSON_VALUE })
public class JobController extends ControllerBase
{
	@Autowired
	private JobManagementService jobManagementService;

	@GetMapping(value = "/")
	@ResponseBody
	public HttpEntity<CollectionModel<JobResource>> getAllJobs()
	{
		return createOkResponse(jobManagementService.getAllJobs(), createResourceAssembler(), getAllJobsLinkBuilder());
	}

	@GetMapping(value = "/{group}/")
	@ResponseBody
	public HttpEntity<CollectionModel<JobResource>> getJobsInGroup(@PathVariable String group)
	{
		return createOkResponse(jobManagementService.getJobsInGroup(group), createResourceAssembler(),
				getJobsInGroupLinkBuilder(group));
	}

	@PutMapping(value = "/{group}/")
	@ResponseBody
	public HttpEntity<CollectionModel<JobResource>> updateJobGroup(@PathVariable String group, @RequestBody Set<JobDto> jobs)
	{
		return createOkResponse(jobManagementService.updateJobGroup(group, jobs), createResourceAssembler(),
				getJobsInGroupLinkBuilder(group));
	}

	@GetMapping(value = "/{group}/{name}")
	@ResponseBody
	@ResponseStatus(HttpStatus.OK)
	public JobResource getJob(@PathVariable String group, @PathVariable String name)
	{
		return new JobResourceAssembler().toModel(jobManagementService.getJob(name, group));
	}

	@PostMapping(value = "/{group}/")
	@ResponseBody
	@ResponseStatus(HttpStatus.CREATED)
	public JobResource addJob(@PathVariable String group, @RequestBody JobDto job)
	{
		job.setGroup(group);
		return new JobResourceAssembler().toModel(jobManagementService.addJob(group, job));
	}

	private JobResourceAssembler createResourceAssembler()
	{
		return new JobResourceAssembler();
	}

	private static WebMvcLinkBuilder getAllJobsLinkBuilder()
	{
		JobController methodOn = methodOn(JobController.class);
		return linkTo(methodOn.getAllJobs());
	}

	private static WebMvcLinkBuilder getJobsInGroupLinkBuilder(String group)
	{
		JobController methodOn = methodOn(JobController.class);
		return linkTo(methodOn.getJobsInGroup(group));
	}

	static WebMvcLinkBuilder getJobLinkBuilder(String group, String name)
	{
		JobController methodOn = methodOn(JobController.class);
		return linkTo(methodOn.getJob(group, name));
	}

	static class JobResource extends EntityModel<JobDto>
	{
		@SuppressWarnings("deprecation") // Constructor will become protected, see spring-projects/spring-hateoas#1297
		public JobResource(JobDto job)
		{
			super(job);
		}
	}

	public static class JobResourceAssembler extends RepresentationModelAssemblerSupport<JobDto, JobResource>
	{
		public JobResourceAssembler()
		{
			super(JobController.class, JobResource.class);
		}

		@Override
		public JobResource toModel(JobDto job)
		{
			JobResource jobResource = instantiateModel(job);
			addSelfLink(jobResource);
			return jobResource;
		}

		@Override
		protected JobResource instantiateModel(JobDto job)
		{
			return new JobResource(job);
		}

		private void addSelfLink(EntityModel<JobDto> job)
		{
			job.add(getLinkBuilder(job).withSelfRel());
		}

		private WebMvcLinkBuilder getLinkBuilder(EntityModel<JobDto> job)
		{
			return JobController.getJobLinkBuilder(job.getContent().getGroup(), job.getContent().getName());
		}
	}

}
