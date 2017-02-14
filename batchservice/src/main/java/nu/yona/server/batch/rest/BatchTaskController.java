package nu.yona.server.batch.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;

import nu.yona.server.batch.client.PinResetConfirmationCodeSendRequestDto;
import nu.yona.server.batch.service.BatchTaskService;

@Controller
@RequestMapping(value = "/batch", produces = { MediaType.APPLICATION_JSON_VALUE })
public class BatchTaskController
{
	@Autowired
	private BatchTaskService batchTaskService;

	@RequestMapping(value = "/sendPinResetConfirmationCode/", method = RequestMethod.POST)
	@ResponseStatus(HttpStatus.OK)
	public void requestPinResetConfirmationCode(
			@RequestBody PinResetConfirmationCodeSendRequestDto pinResetConfirmationCodeSendRequest)
	{
		batchTaskService.requestPinResetConfirmationCode(pinResetConfirmationCodeSendRequest);
	}
}
