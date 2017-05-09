package nu.yona.server.sms;

public enum SmsTemplate
{
	ADD_USER_NUMBER_CONFIRMATION("add-user-number-confirmation"), CHANGED_USER_NUMBER_CONFIRMATION(
			"changed-user-number-confirmation"), OVERWRITE_USER_CONFIRMATION(
					"overwrite-user-confirmation"), PIN_RESET_REQUEST_CONFIRMATION(
							"pin-reset-request-confirmation"), BUDDY_INVITE("buddy-invitation");

	private final String name;

	private SmsTemplate(String name)
	{
		this.name = name;
	}

	public String getName()
	{
		return name;
	}
}
