package nu.yona.server.repro2;

import org.springframework.hateoas.ResourceSupport;

import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({ @Type(value = Cat2.class, name = "Cat2"), @Type(value = Dog2.class, name = "Dog2") })
@JsonRootName("Animal2")
public abstract class Animal2 extends ResourceSupport implements Cloneable
{

	private String name;

	public Animal2()
	{
	}

	public Animal2(String name)
	{
		super();
		this.name = name;
	}

	public String getName()
	{
		return name;
	}

	public void setName(String name)
	{
		this.name = name;
	}

	@Override
	protected Object clone()
	{
		try
		{
			return super.clone();
		}
		catch (CloneNotSupportedException e)
		{
			// Never happens
			return null;
		}
	}
}