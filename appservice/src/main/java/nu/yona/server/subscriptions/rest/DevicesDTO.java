package nu.yona.server.subscriptions.rest;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonRootName("devices")
public class DevicesDTO {
	private Set<String> devices;

	@JsonCreator
	public DevicesDTO(@JsonProperty("devices") @JsonDeserialize(as = TreeSet.class, contentAs = String.class) Set<String> devices) {
		this.devices = new HashSet<>(devices);
	}
	
	public Set<String> getCategories() {
		return Collections.unmodifiableSet(devices);
	}
}
