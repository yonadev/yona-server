/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

import com.hazelcast.core.Hazelcast;

import nu.yona.server.properties.YonaProperties;

@SpringBootApplication
@EnableCaching
public class AppServiceApplication
{
	@Autowired
	private YonaProperties yonaProperties;

	public static void main(String[] args)
	{
		try
		{
			SpringApplication.run(AppServiceApplication.class, args);
		}
		catch (Exception ex)
		{
			// Issue in Hazelcast: it doesn't shutdown automatically: see https://github.com/hazelcast/hazelcast/issues/6339
			// If service start up fails, we want the the process to exit, so in that case we are shutting down Hazelcast
			// explicitly.
			Hazelcast.shutdownAll();
			throw ex;
		}
	}

	@Bean
	public WebMvcConfigurer corsConfigurer()
	{
		return new WebMvcConfigurerAdapter() {
			@Override
			public void addCorsMappings(CorsRegistry registry)
			{
				registry.addMapping("/swagger/swagger-spec.yaml");
				if (yonaProperties.getSecurity().isCorsAllowed())
				{
					// Enable CORS for the other resources, to allow testing the API through Swagger UI.
					registry.addMapping("/**").allowedMethods("GET", "HEAD", "POST", "PUT", "DELETE");
				}
			}
		};
	}
}
