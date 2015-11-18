package nu.yona.server.analysis.rest;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonRootName("categories")
public class CategoriesDTO
{
    private Set<String> categories;

    @JsonCreator
    public CategoriesDTO(
            @JsonProperty("categories") @JsonDeserialize(as = TreeSet.class, contentAs = String.class) Set<String> categories)
    {
        this.categories = new HashSet<>(categories);
    }

    public Set<String> getCategories()
    {
        return Collections.unmodifiableSet(categories);
    }
}
