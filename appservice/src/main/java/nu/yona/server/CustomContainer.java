/*******************************************************************************
 * Copyright (c) 2016, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server;

import org.springframework.boot.web.server.MimeMappings;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.stereotype.Component;

@Component
public class CustomContainer implements WebServerFactoryCustomizer<ConfigurableServletWebServerFactory>
{
	@Override
	public void customize(ConfigurableServletWebServerFactory factory)
	{
		MimeMappings mappings = new MimeMappings(MimeMappings.DEFAULT);
		mappings.add("cer", "application/pkix-cert"); // see resources/rootcert.cer
		mappings.add("yaml", "text/yaml"); // Swagger spec
		factory.setMimeMappings(mappings);
	}
}