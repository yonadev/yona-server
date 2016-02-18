package nu.yona.server.analysis.rest;

import static nu.yona.server.rest.Constants.PASSWORD_HEADER;

import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import nu.yona.server.analysis.service.AnalysisEngineService;
import nu.yona.server.analysis.service.AppActivityDTO;
import nu.yona.server.crypto.CryptoSession;
import nu.yona.server.subscriptions.service.UserService;

@Controller
@RequestMapping(value = "/users/{userID}/activity")
public class ActivityController
{
	@Autowired
	private AnalysisEngineService analysisEngineService;

	@Autowired
	private UserService userService;

	@RequestMapping(value = "/appActivity/", method = RequestMethod.POST)
	@ResponseBody
	@ResponseStatus(HttpStatus.OK)
	public void addAppActivity(@RequestHeader(value = PASSWORD_HEADER) Optional<String> password, @PathVariable UUID userID,
			@RequestBody AppActivityDTO appActivity)
	{
		CryptoSession.execute(password, () -> userService.canAccessPrivateData(userID), () -> {
			analysisEngineService.analyze(userID, appActivity);
			return null;
		});
	}
}
