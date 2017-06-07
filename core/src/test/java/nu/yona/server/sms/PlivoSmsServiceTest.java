package nu.yona.server.sms;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collection;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import nu.yona.server.properties.SmsProperties;
import nu.yona.server.properties.YonaProperties;

@RunWith(Parameterized.class)
public class PlivoSmsServiceTest
{
	private static final String TEST_ALPHA_ID = "TestId";
	private static final String TEST_DEFAULT_NUMBER = "+12345678";
	private static final String TEST_ALPHA_SUPPORTING_COUNTRY_CALLING_CODES = "+31 +49";

	@Rule
	public MockitoRule rule = MockitoJUnit.rule();

	@Parameters
	public static Collection<Object[]> data()
	{
		return Arrays.asList(new Object[][] { { "+31000000000", TEST_ALPHA_ID }, { "+32111111111", TEST_DEFAULT_NUMBER },
				{ "+49222222222", TEST_ALPHA_ID } });
	}

	@Parameter // first data value (0) is default
	public String targetPhoneNumber;

	@Parameter(1)
	public String expectedSender;

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
	public void test()
	{
		assertThat(smsService.determineSender(targetPhoneNumber), equalTo(expectedSender));
	}

}
