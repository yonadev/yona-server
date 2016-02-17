package nu.yona.server.repro2;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

import java.util.Arrays;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.PagedResources;
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
@RequestMapping(value = "/animals2")
public class AnimalController2
{
	private Animal2 dog = new Dog2("nameofdog", 1.0);
	private Animal2 cat = new Cat2("nameofcat", 3);
	private Animal2 dog2 = new Dog2("nameofdog2", 10.2);

	@RequestMapping(value = "/", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<PagedResources<Animal2>> getAnimals(Pageable pageable,
			PagedResourcesAssembler<Animal2> pagedResourcesAssembler)
	{

		return createOKResponse(pagedResourcesAssembler.toResource(makePage(Arrays.asList(dog, cat, dog2), pageable),
				new AnimalResourceAssembler()));
	}

	@RequestMapping(value = "/{name}", method = RequestMethod.GET)
	@ResponseBody
	public HttpEntity<Animal2> getAnimal(@PathVariable String name)
	{
		return createOKResponse(new AnimalResourceAssembler().instantiateResource(findAnimal(name)));
	}

	private Page<Animal2> makePage(List<Animal2> animals, Pageable pageable)
	{
		return new PageImpl<Animal2>(animals, pageable, animals.size());
	}

	private Animal2 findAnimal(String name)
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

	private HttpEntity<PagedResources<Animal2>> createOKResponse(PagedResources<Animal2> animals)
	{
		return new ResponseEntity<PagedResources<Animal2>>(animals, HttpStatus.OK);
	}

	private HttpEntity<Animal2> createOKResponse(Animal2 animalResource)
	{
		return new ResponseEntity<Animal2>(animalResource, HttpStatus.OK);
	}

	static ControllerLinkBuilder getAnimalLinkBuilder(String name)
	{
		AnimalController2 methodOn = methodOn(AnimalController2.class);
		return linkTo(methodOn.getAnimal(name));
	}

	private static class AnimalResourceAssembler extends ResourceAssemblerSupport<Animal2, Animal2>
	{
		public AnimalResourceAssembler()
		{
			super(AnimalController2.class, Animal2.class);
		}

		@Override
		public Animal2 toResource(Animal2 animal)
		{
			Animal2 animalResource = instantiateResource(animal);
			ControllerLinkBuilder selfLinkBuilder = getAnimalLinkBuilder(animal.getName());
			addSelfLink(selfLinkBuilder, animalResource);
			return animalResource;
		}

		@Override
		protected Animal2 instantiateResource(Animal2 animal)
		{
			return (Animal2) animal.clone();
		}

		private void addSelfLink(ControllerLinkBuilder selfLinkBuilder, Animal2 animalResource)
		{
			animalResource.add(selfLinkBuilder.withSelfRel());
		}
	}
}