package nu.yona.server.repro;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

import java.util.Arrays;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.PagedResources;
import org.springframework.hateoas.Resource;
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

@Controller
@RequestMapping(value = "/animals")
public class AnimalController
{
	private Animal dog = new Dog("nameofdog", 1.0);
	private Animal cat = new Cat("nameofcat", 3);
	private Animal dog2 = new Dog("nameofdog2", 10.2);

	@RequestMapping(value = "/", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<PagedResources<AnimalResource>> getAnimals(Pageable pageable,
			PagedResourcesAssembler<Animal> pagedResourcesAssembler)
	{

		return createOKResponse(pagedResourcesAssembler.toResource(makePage(Arrays.asList(dog, cat, dog2), pageable),
				new AnimalResourceAssembler()));
	}

	private Page<Animal> makePage(List<Animal> animals, Pageable pageable)
	{
		return new PageImpl<Animal>(animals, pageable, animals.size());
	}

	@RequestMapping(value = "/{name}", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<AnimalResource> getAnimal(@PathVariable String name)
	{
		return createOKResponse(new AnimalResourceAssembler().toResource(findAnimal(name)));
	}

	private Animal findAnimal(String name)
	{
		switch (name)
		{
			case "nameofdog":
				return dog;
			case "nameofcat":
				return cat;
			case "nameofdog2":
				return dog2;
			default:
				return null;
		}
	}

	private HttpEntity<PagedResources<AnimalResource>> createOKResponse(PagedResources<AnimalResource> animals)
	{
		return new ResponseEntity<PagedResources<AnimalResource>>(animals, HttpStatus.OK);
	}

	private HttpEntity<AnimalResource> createOKResponse(AnimalResource animalResource)
	{
		return new ResponseEntity<AnimalResource>(animalResource, HttpStatus.OK);
	}

	static ControllerLinkBuilder getAnimalLinkBuilder(String name)
	{
		AnimalController methodOn = methodOn(AnimalController.class);
		return linkTo(methodOn.getAnimal(name));
	}

	static class AnimalResource extends Resource<Animal>
	{
		public AnimalResource(Animal animal)
		{
			super(animal);
		}
	}

	private static class AnimalResourceAssembler extends ResourceAssemblerSupport<Animal, AnimalResource>
	{
		public AnimalResourceAssembler()
		{
			super(AnimalController.class, AnimalResource.class);
		}

		@Override
		public AnimalResource toResource(Animal animal)
		{
			AnimalResource animalResource = instantiateResource(animal);
			ControllerLinkBuilder selfLinkBuilder = getAnimalLinkBuilder(animal.getName());
			addSelfLink(selfLinkBuilder, animalResource);
			return animalResource;
		}

		@Override
		protected AnimalResource instantiateResource(Animal animal)
		{
			return new AnimalResource(animal);
		}

		private void addSelfLink(ControllerLinkBuilder selfLinkBuilder, Resource<Animal> animalResource)
		{
			animalResource.add(selfLinkBuilder.withSelfRel());
		}
	}
}