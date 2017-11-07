package nu.yona.server.rest;

import java.util.Collection;

import org.springframework.data.domain.Page;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.PagedResources;
import org.springframework.hateoas.ResourceSupport;
import org.springframework.hateoas.Resources;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;
import org.springframework.hateoas.mvc.ResourceAssemblerSupport;
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

	protected <T> ResponseEntity<T> createResponse(HttpStatus status)
	{
		return new ResponseEntity<>(status);
	}

	protected <T, U extends ResourceSupport> ResponseEntity<U> createOkResponse(T dto,
			ResourceAssemblerSupport<T, U> resourceAssembler)
	{
		return createResponse(dto, HttpStatus.OK, resourceAssembler);
	}

	protected <T, U extends ResourceSupport> ResponseEntity<U> createResponse(T dto, HttpStatus status,
			ResourceAssemblerSupport<T, U> resourceAssembler)
	{
		return new ResponseEntity<>(resourceAssembler.toResource(dto), status);
	}

	protected <T, U extends ResourceSupport> ResponseEntity<Resources<U>> createOkResponse(Collection<T> dtos,
			ResourceAssemblerSupport<T, U> resourceAssembler, ControllerLinkBuilder linkBuilder)
	{
		return createResponse(dtos, HttpStatus.OK, resourceAssembler, linkBuilder);
	}

	protected <T, U extends ResourceSupport> ResponseEntity<Resources<U>> createResponse(Collection<T> dtos, HttpStatus status,
			ResourceAssemblerSupport<T, U> resourceAssembler, ControllerLinkBuilder linkBuilder)
	{
		return new ResponseEntity<>(createCollectionResource(dtos, resourceAssembler, linkBuilder), status);
	}

	protected <T, U extends ResourceSupport> ResponseEntity<PagedResources<U>> createOkResponse(Page<T> dtos,
			PagedResourcesAssembler<T> pagedResourceAssembler, ResourceAssemblerSupport<T, U> resourceAssembler)
	{
		return createResponse(dtos, HttpStatus.OK, pagedResourceAssembler, resourceAssembler);
	}

	protected <T, U extends ResourceSupport> ResponseEntity<PagedResources<U>> createResponse(Page<T> dtos, HttpStatus status,
			PagedResourcesAssembler<T> pagedResourceAssembler, ResourceAssemblerSupport<T, U> resourceAssembler)
	{
		return new ResponseEntity<>(pagedResourceAssembler.toResource(dtos, resourceAssembler), status);
	}

	public static <T, U extends ResourceSupport> Resources<U> createCollectionResource(Collection<T> dtos,
			ResourceAssemblerSupport<T, U> resourceAssembler, ControllerLinkBuilder linkBuilder)
	{
		return new Resources<>(resourceAssembler.toResources(dtos), linkBuilder.withSelfRel());
	}

}