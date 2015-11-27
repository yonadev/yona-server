package nu.yona.server.sms;

public interface SmsService
{
	void send(String phoneNumber, String message);
}
