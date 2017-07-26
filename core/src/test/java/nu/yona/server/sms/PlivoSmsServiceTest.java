package nu.yona.server.sms;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import nu.yona.server.properties.SmsProperties;
import nu.yona.server.properties.YonaProperties;

@RunWith(JUnitParamsRunner.class)
public class PlivoSmsServiceTest
{
	private static final String TEST_ALPHA_ID = "TestId";
	private static final String TEST_DEFAULT_NUMBER = "+12345678";
	private static final String TEST_ALPHA_SUPPORTING_COUNTRY_CALLING_CODES = "+31 +49";

	@Rule
	public MockitoRule rule = MockitoJUnit.rule();

	@Mock
	private YonaProperties mockYonaProperties;

	@InjectMocks
	private final PlivoSmsService smsService = new PlivoSmsService();

	@Before
	public void setUp()
	{
		SmsProperties smsProperties = new SmsProperties();
		smsProperties.setAlphaSenderId(TEST_ALPHA_ID);
		smsProperties.setDefaultSenderNumber(TEST_DEFAULT_NUMBER);
		smsProperties.setAlphaSenderSupportingCountryCallingCodes(TEST_ALPHA_SUPPORTING_COUNTRY_CALLING_CODES);
		when(mockYonaProperties.getSms()).thenReturn(smsProperties);
	}

	@Test
	@Parameters({ "+31000000000", "+49222222222" })
	public void determineSender_targetPhoneNumberInAlphaSenderSupportingCountries_returnsAlphaId(String targetPhoneNumber)
	{
		assertThat(smsService.determineSender(targetPhoneNumber), equalTo(TEST_ALPHA_ID));
	}

	@Test
	public void determineSender_targetPhoneNumberNotInAlphaSenderSupportingCountries_returnsDefaultNumber()
	{
		assertThat(smsService.determineSender("+32111111111"), equalTo(TEST_DEFAULT_NUMBER));
	}
}
