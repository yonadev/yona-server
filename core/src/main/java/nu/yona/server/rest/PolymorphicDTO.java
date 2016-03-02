package nu.yona.server.rest;

import org.springframework.hateoas.ResourceSupport;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/*
 * Class to extend from when a DTO is polymorphic. Do not forget to add an @JsonSubTypes annotation.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "@type")
public abstract class PolymorphicDTO extends ResourceSupport
{
	/*
	 * Used to serialise the type. The default way with @JsonTypeInfo did not work.
	 */
	@JsonProperty(value = "@type")
	public abstract String getType();

	/*
	 * Revert to default equals implementation.
	 * @see org.springframework.hateoas.ResourceSupport#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj)
	{
		return this == obj;
	}

	/*
	 * Revert to default hash code implementation.
	 * @see org.springframework.hateoas.ResourceSupport#hashCode()
	 */
	@Override
	public int hashCode()
	{
		return System.identityHashCode(this);
	}
}
