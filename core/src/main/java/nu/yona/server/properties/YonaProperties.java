package nu.yona.server.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties("yona")
@Configuration
public class YonaProperties
{
	@NestedConfigurationProperty
	private final AnalysisServiceProperties analysisService = new AnalysisServiceProperties();

	@NestedConfigurationProperty
	private final EmailProperties email = new EmailProperties();

	@NestedConfigurationProperty
	private final LdapProperties ldap = new LdapProperties();

	@NestedConfigurationProperty
	private final SecurityProperties security = new SecurityProperties();

	@NestedConfigurationProperty
	private final SmsProperties sms = new SmsProperties();

	public AnalysisServiceProperties getAnalysisService()
	{
		return analysisService;
	}

	public EmailProperties getEmail()
	{
		return email;
	}

	public LdapProperties getLdap()
	{
		return ldap;
	}

	public SecurityProperties getSecurity()
	{
		return security;
	}

	public SmsProperties getSms()
	{
		return sms;
	}
}
