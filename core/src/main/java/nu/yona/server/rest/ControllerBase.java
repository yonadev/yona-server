/*******************************************************************************
 * Copyright (c) 2017, 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.rest;

import java.util.Collection;

import org.springframework.data.domain.Page;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public abstract class ControllerBase
{

	protected ControllerBase()
	{
		super();
	}

	protected <T> ResponseEntity<T> createOkResponse()
	{
		return createResponse(HttpStatus.OK);
	}

	protected <T> ResponseEntity<T> createNoContentResponse()
	{
		return createResponse(HttpStatus.NO_CONTENT);
	}

	protected <T> ResponseEntity<T> createResponse(HttpStatus status)
	{
		return new ResponseEntity<>(status);
	}

	protected <T, U extends RepresentationModel<?>> ResponseEntity<U> createOkResponse(T dto,
			RepresentationModelAssembler<T, U> resourceAssembler)
	{
		return createResponse(dto, HttpStatus.OK, resourceAssembler);
	}

	protected <T, U extends RepresentationModel<?>> ResponseEntity<U> createResponse(T dto, HttpStatus status,
			RepresentationModelAssembler<T, U> resourceAssembler)
	{
		return new ResponseEntity<>(resourceAssembler.toModel(dto), status);
	}

	protected <T, U extends RepresentationModel<?>> ResponseEntity<CollectionModel<U>> createOkResponse(Collection<T> dtos,
			RepresentationModelAssembler<T, U> resourceAssembler, WebMvcLinkBuilder linkBuilder)
	{
		return createResponse(dtos, HttpStatus.OK, resourceAssembler, linkBuilder);
	}

	protected <T, U extends RepresentationModel<?>> ResponseEntity<CollectionModel<U>> createResponse(Collection<T> dtos,
			HttpStatus status, RepresentationModelAssembler<T, U> resourceAssembler, WebMvcLinkBuilder linkBuilder)
	{
		return new ResponseEntity<>(createCollectionResource(dtos, resourceAssembler, linkBuilder), status);
	}

	protected <T, U extends RepresentationModel<?>> ResponseEntity<PagedModel<U>> createOkResponse(Page<T> dtos,
			PagedResourcesAssembler<T> pagedResourceAssembler, RepresentationModelAssembler<T, U> resourceAssembler)
	{
		return createResponse(dtos, HttpStatus.OK, pagedResourceAssembler, resourceAssembler);
	}

	protected <T, U extends RepresentationModel<?>> ResponseEntity<PagedModel<U>> createResponse(Page<T> dtos, HttpStatus status,
			PagedResourcesAssembler<T> pagedResourceAssembler, RepresentationModelAssembler<T, U> resourceAssembler)
	{
		return new ResponseEntity<>(pagedResourceAssembler.toModel(dtos, resourceAssembler), status);
	}

	public static <T, U extends RepresentationModel<?>> CollectionModel<U> createCollectionResource(Collection<T> dtos,
			RepresentationModelAssembler<T, U> resourceAssembler, WebMvcLinkBuilder linkBuilder)
	{
		return CollectionModel.of(resourceAssembler.toCollectionModel(dtos), linkBuilder.withSelfRel());
	}

}
