/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.goals.rest;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import org.springframework.hateoas.ExposesResourceFor;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.Resources;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;
import org.springframework.hateoas.mvc.ResourceAssemblerSupport;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonRootName;

@Controller
@ExposesResourceFor(ReproController.ReproResource.class)
@RequestMapping(value = "/repro/")
public class ReproController {

	@RequestMapping(method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<Resources<ReproResource>> reproduce() {
		return createOKResponse(Collections.emptySet());
	}

	private HttpEntity<Resources<ReproResource>> createOKResponse(Set<ReproDTO> repros) {
		return new ResponseEntity<Resources<ReproResource>>(wrapReprosAsResourceList(repros), HttpStatus.OK);
	}

	private Resources<ReproResource> wrapReprosAsResourceList(Set<ReproDTO> repros) {
		return new Resources<>(new ReproResourceAssembler().toResources(repros),
				getAllReprosLinkBuilder().withSelfRel());
	}

	static ControllerLinkBuilder getAllReprosLinkBuilder() {
		ReproController methodOn = methodOn(ReproController.class);
		return linkTo(methodOn.reproduce());
	}

	@JsonRootName("Repro")
	public class ReproDTO {
		private final UUID id;

		@JsonCreator
		public ReproDTO() {
			this.id = null;
		}

		public UUID getID() {
			return id;
		}
	}

	public static class ReproResource extends Resource<ReproDTO> {
		public ReproResource(ReproDTO repro) {
			super(repro);
		}
	}

	private static class ReproResourceAssembler extends ResourceAssemblerSupport<ReproDTO, ReproResource> {
		public ReproResourceAssembler() {
			super(ReproController.class, ReproResource.class);
		}

		@Override
		public ReproResource toResource(ReproDTO repro) {
			return super.createResourceWithId(repro.getID(), repro);
		}

		@Override
		protected ReproResource instantiateResource(ReproDTO repro) {
			return new ReproResource(repro);
		}
	}
}
