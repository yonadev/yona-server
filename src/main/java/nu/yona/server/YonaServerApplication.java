/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.hateoas.RelProvider;
import org.springframework.hateoas.config.EnableHypermediaSupport;
import org.springframework.hateoas.config.EnableHypermediaSupport.HypermediaType;

import nu.yona.server.entities.RepositoryProvider;
import nu.yona.server.rest.JsonRootRelProvider;

@SpringBootApplication
@EnableHypermediaSupport(type = HypermediaType.HAL)
public class YonaServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(YonaServerApplication.class, args);
	}

	@Bean
	RelProvider relProvider() {
		return new JsonRootRelProvider();
	}

	@Bean
	RepositoryProvider repositoryProvider() {
		return new RepositoryProvider();
	}
}
