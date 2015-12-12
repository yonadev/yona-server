package nu.yona.server.sms;

import java.util.Map;

public interface SmsService
{
	void send(String phoneNumber, String messageTemplateName, Map<String, Object> templateParameters);

	public static final String TemplateName_AddUserNumberConfirmation = "add-user-number-confirmation";
	public static final String TemplateName_ChangedUserNumberConfirmation = "changed-user-number-confirmation";
	public static final String TemplateName_OverwriteUserNumberConfirmation = "overwrite-user-number-confirmation";
	public static final String TemplateName_BuddyInvite = "buddy-invite";
}
