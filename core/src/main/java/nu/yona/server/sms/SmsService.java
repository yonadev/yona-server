package nu.yona.server.sms;

import java.util.Map;
import java.util.Optional;

public interface SmsService
{
	void send(String destinationPhoneNumber, String messageTemplateName, Map<String, Object> templateParameters,
			Optional<String> sourcePShoneNumber);

	public static final String TemplateName_AddUserNumberConfirmation = "add-user-number-confirmation";
	public static final String TemplateName_ChangedUserNumberConfirmation = "changed-user-number-confirmation";
	public static final String TemplateName_OverwriteUserNumberConfirmation = "overwrite-user-number-confirmation";
	public static final String TemplateName_BuddyInvite = "buddy-invitation";
}
