/*
 * Copyright (c) 2022 Stichting Yona Foundation
 * This Source Code Form is subject to the terms of the Mozilla Public License, v.2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package nu.yona.server.goals.service;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

import nu.yona.server.exceptions.InvalidDataException;

class ActivityCategoryDtoTest
{
	private final UUID id = UUID.randomUUID();
	private final Map<Locale, String> localizableName = Map.of(Locale.US, "ValidCategory", Locale.CANADA_FRENCH,
			"CatégorieValide");
	private final boolean mandatory = true;
	private final Set<String> smoothwallCategories = Set.of("valid", "category");
	private final Set<String> applications = Set.of("app1", "app2");
	private final Map<Locale, String> localizableDescription = Map.of(Locale.US, "This is a valid category", Locale.CANADA_FRENCH,
			"Ceci est une catégorie valide");

	@Test
	void assertValidCharacters_constructor_noException()
	{
		ActivityCategoryDto dto = new ActivityCategoryDto(id, localizableName, mandatory, smoothwallCategories, applications,
				localizableDescription);
		assertThat(dto.getId(), equalTo(id));
		localizableName.keySet().forEach(k -> assertNameForLocale(k, dto));
		localizableDescription.keySet().forEach(k -> assertDescriptionForLocale(k, dto));
	}

	private void assertNameForLocale(Locale us, ActivityCategoryDto dto)
	{
		LocaleContextHolder.setLocale(us);
		assertThat(dto.getName(), equalTo(localizableName.get(us)));
	}

	private void assertDescriptionForLocale(Locale us, ActivityCategoryDto dto)
	{
		LocaleContextHolder.setLocale(us);
		assertThat(dto.getDescription(), equalTo(localizableDescription.get(us)));
	}

	@Test
	void assertInvalidCharactersInNameUs_constructor_throwsInvalidDataException()
	{
		Map<Locale, String> invalidLocalizableNameUs = Map.of(Locale.US, "Valid<Category>", Locale.CANADA_FRENCH,
				"CatégorieValide");
		InvalidDataException exception = assertThrows(InvalidDataException.class,
				() -> new ActivityCategoryDto(id, invalidLocalizableNameUs, mandatory, smoothwallCategories, applications,
						localizableDescription));
		assertThat(exception.getMessageId(), equalTo("error.request.contains.invalid.characters"));
		assertThat(exception.getMessage(), containsString("localizableName, locale " + Locale.US));
	}

	@Test
	void assertInvalidCharactersInNameFr_constructor_throwsInvalidDataException()
	{
		Map<Locale, String> invalidLocalizableNameCa = Map.of(Locale.US, "ValidCategory", Locale.CANADA_FRENCH,
				"Catégorie<Valide>");
		InvalidDataException exception = assertThrows(InvalidDataException.class,
				() -> new ActivityCategoryDto(id, invalidLocalizableNameCa, mandatory, smoothwallCategories, applications,
						localizableDescription));
		assertThat(exception.getMessageId(), equalTo("error.request.contains.invalid.characters"));
		assertThat(exception.getMessage(), containsString("localizableName, locale " + Locale.CANADA_FRENCH));
	}

	@Test
	void assertInvalidCharactersInDescriptionUs_constructor_throwsInvalidDataException()
	{
		Map<Locale, String> invalidLocalizableDescriptionUs = Map.of(Locale.US, "Valid<Category>", Locale.CANADA_FRENCH,
				"CatégorieValide");
		InvalidDataException exception = assertThrows(InvalidDataException.class,
				() -> new ActivityCategoryDto(id, localizableName, mandatory, smoothwallCategories, applications,
						invalidLocalizableDescriptionUs));
		assertThat(exception.getMessageId(), equalTo("error.request.contains.invalid.characters"));
		assertThat(exception.getMessage(), containsString("localizableDescription, locale " + Locale.US));
	}

	@Test
	void assertInvalidCharactersInDescriptionFr_constructor_throwsInvalidDataException()
	{
		Map<Locale, String> invalidLocalizableDescriptionCa = Map.of(Locale.US, "ValidCategory", Locale.CANADA_FRENCH,
				"Catégorie<Valide>");
		InvalidDataException exception = assertThrows(InvalidDataException.class,
				() -> new ActivityCategoryDto(id, localizableName, mandatory, smoothwallCategories, applications,
						invalidLocalizableDescriptionCa));
		assertThat(exception.getMessageId(), equalTo("error.request.contains.invalid.characters"));
		assertThat(exception.getMessage(), containsString("localizableDescription, locale " + Locale.CANADA_FRENCH));
	}
}
