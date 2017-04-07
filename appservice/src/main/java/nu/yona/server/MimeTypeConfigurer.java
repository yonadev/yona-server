/*******************************************************************************
 * Copyright (c) 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server;

import org.springframework.boot.context.embedded.ConfigurableEmbeddedServletContainer;
import org.springframework.boot.context.embedded.EmbeddedServletContainerCustomizer;
import org.springframework.boot.context.embedded.MimeMappings;
import org.springframework.stereotype.Component;

@Component
public class MimeTypeConfigurer implements EmbeddedServletContainerCustomizer
{
	@Override
	public void customize(ConfigurableEmbeddedServletContainer container)
	{
		MimeMappings mappings = new MimeMappings(MimeMappings.DEFAULT);
		mappings.add("cer", "application/pkix-cert"); // see resources/rootcert.cer
		container.setMimeMappings(mappings);
	}
}