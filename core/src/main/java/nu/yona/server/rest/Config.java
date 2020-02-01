/*******************************************************************************
 * Copyright (c) 2019, 2020 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v.2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.rest;

import java.util.Collections;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class Config implements WebMvcConfigurer
{
	@Autowired
	private PassThroughHeadersHolder headersHolder;

	@Override
	public void addInterceptors(InterceptorRegistry registry)
	{
		registry.addInterceptor(new HeadersServerInterceptor(headersHolder));
	}

	@Bean
	public HeadersClientInterceptor getHeadersClientInterceptor()
	{
		return new HeadersClientInterceptor(headersHolder);
	}

	@Bean
	public RestTemplate restTemplate(ObjectMapper objectMapper)
	{
		RestTemplate restTemplate = new RestTemplate();
		restTemplate.setErrorHandler(new RestClientErrorHandler(objectMapper));
		restTemplate.setInterceptors(Collections.singletonList(getHeadersClientInterceptor()));
		return restTemplate;
	}
}