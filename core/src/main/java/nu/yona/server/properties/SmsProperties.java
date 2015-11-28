package nu.yona.server.properties;

public class SmsProperties
{
	private boolean isEnabled = false;
	private String mobileNumberConfirmationMessage = "Yona confirmation code: {0}";
	private int mobileNumberConfirmationCodeDigits = 5;
	private String senderNumber = "";
	private String plivoUrl = "https://api.plivo.com/v1/Account/{0}/Message/";
	private String plivoAuthId = "";
	private String plivoAuthToken = "";

	public boolean isEnabled()
	{
		return isEnabled;
	}

	public void setEnabled(boolean isEnabled)
	{
		this.isEnabled = isEnabled;
	}

	public String getMobileNumberConfirmationMessage()
	{
		return mobileNumberConfirmationMessage;
	}

	public void setMobileNumberConfirmationMessage(String mobileNumberConfirmationMessage)
	{
		this.mobileNumberConfirmationMessage = mobileNumberConfirmationMessage;
	}

	public int getMobileNumberConfirmationCodeDigits()
	{
		return mobileNumberConfirmationCodeDigits;
	}

	public void setMobileNumberConfirmationCodeDigits(int mobileNumberConfirmationCodeDigits)
	{
		this.mobileNumberConfirmationCodeDigits = mobileNumberConfirmationCodeDigits;
	}

	public String getSenderNumber()
	{
		return senderNumber;
	}

	public void setSenderNumber(String senderNumber)
	{
		this.senderNumber = senderNumber;
	}

	public String getPlivoUrl()
	{
		return plivoUrl;
	}

	public void setPlivoUrl(String plivoUrl)
	{
		this.plivoUrl = plivoUrl;
	}

	public String getPlivoAuthId()
	{
		return plivoAuthId;
	}

	public void setPlivoAuthId(String plivoAuthId)
	{
		this.plivoAuthId = plivoAuthId;
	}

	public String getPlivoAuthToken()
	{
		return plivoAuthToken;
	}

	public void setPlivoAuthToken(String plivoAuthToken)
	{
		this.plivoAuthToken = plivoAuthToken;
	}
}
