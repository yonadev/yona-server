package nu.yona.server;

import org.springframework.boot.context.embedded.ConfigurableEmbeddedServletContainer;
import org.springframework.boot.context.embedded.EmbeddedServletContainerCustomizer;
import org.springframework.boot.context.embedded.MimeMappings;
import org.springframework.stereotype.Component;

@Component
public class MimeTypeConfigurer implements EmbeddedServletContainerCustomizer
{
	public static final String CERT_MIME_TYPE = "application/x-x509-ca-cert";

	@Override
	public void customize(ConfigurableEmbeddedServletContainer container)
	{
		MimeMappings mappings = new MimeMappings(MimeMappings.DEFAULT);
		mappings.add("ovpn", "application/x-openvpn-profile");
		mappings.add("crt", CERT_MIME_TYPE);
		container.setMimeMappings(mappings);
	}
}