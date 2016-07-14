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
		mappings.add("ovpn", "application/x-openvpn-profile");
		mappings.add("cer", "application/pkix-cert");
		container.setMimeMappings(mappings);
	}
}