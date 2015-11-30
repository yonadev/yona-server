/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server;

import static com.google.common.collect.Lists.newArrayList;

import org.apache.velocity.app.VelocityEngine;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.hateoas.config.EnableHypermediaSupport;
import org.springframework.hateoas.config.EnableHypermediaSupport.HypermediaType;
import org.springframework.web.bind.annotation.RequestMethod;

import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.builders.ResponseMessageBuilder;
import springfox.documentation.schema.ModelRef;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;

@SpringBootApplication
// @EnableSwagger2
@EnableHypermediaSupport(type = HypermediaType.HAL)
public class AppServiceApplication
{
	public static void main(String[] args)
	{
		SpringApplication.run(AppServiceApplication.class, args);
	}

	@Bean
	public Docket yonaApi()
	{
		ApiInfo apiInfo = new ApiInfo("Yona Server", "Server backing the Yona mobile app", "1.0", null, null, "MPL",
				"https://www.mozilla.org/en-US/MPL/2.0/");
		return new Docket(DocumentationType.SWAGGER_2).apiInfo(apiInfo).select().apis(RequestHandlerSelectors.any())
				.paths(PathSelectors.any()).build().pathMapping("/").useDefaultResponseMessages(false)
				.globalResponseMessage(RequestMethod.GET, newArrayList(new ResponseMessageBuilder().code(500)
						.message("500 message").responseModel(new ModelRef("Error")).build()));
	}

	@Bean
	public VelocityEngine velocityTemplateEngine()
	{
		VelocityEngine engine = new VelocityEngine();

		engine.addProperty("resource.loader", "class");
		engine.addProperty("class.resource.loader.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");

		return engine;
	}
}