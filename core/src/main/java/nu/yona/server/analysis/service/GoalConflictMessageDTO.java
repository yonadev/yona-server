/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.analysis.entities.GoalConflictMessage;
import nu.yona.server.analysis.entities.GoalConflictMessage.Status;
import nu.yona.server.goals.service.GoalServiceException;
import nu.yona.server.messaging.entities.DiscloseRequestMessage;
import nu.yona.server.messaging.entities.Message;
import nu.yona.server.messaging.entities.MessageDestination;
import nu.yona.server.messaging.service.MessageActionDTO;
import nu.yona.server.messaging.service.MessageDTO;
import nu.yona.server.messaging.service.MessageService.DTOManager;
import nu.yona.server.messaging.service.MessageService.TheDTOManager;
import nu.yona.server.subscriptions.entities.UserAnonymized;
import nu.yona.server.subscriptions.service.BuddyDTO;
import nu.yona.server.subscriptions.service.BuddyService;
import nu.yona.server.subscriptions.service.UserDTO;

@JsonRootName("goalConflictMessage")
public class GoalConflictMessageDTO extends MessageDTO
{
	private static final String SELF_NICKNAME = "<self>";
	private static final String REQUEST_DISCLOSURE = "requestDisclosure";
	private final String nickname;
	private final String goalName;
	private final String url;
	private Status status;

	private GoalConflictMessageDTO(UUID id, Date creationTime, String nickname, String goalName, String url, Status status)
	{
		super(id, creationTime);
		this.nickname = nickname;
		this.goalName = goalName;
		this.url = url;
		this.status = status;
	}

	@Override
	public Set<String> getPossibleActions()
	{
		Set<String> possibleActions = new HashSet<>();
		if (this.isFromBuddy() && this.status == Status.ANNOUNCED)
		{
			possibleActions.add(REQUEST_DISCLOSURE);
		}
		return possibleActions;
	}

	private boolean isFromBuddy()
	{
		return nickname != null && !nickname.equals(SELF_NICKNAME);
	}

	public String getNickname()
	{
		return nickname;
	}

	public String getGoalName()
	{
		return goalName;
	}

	public Status getStatus()
	{
		return status;
	}

	public String getUrl()
	{
		return url;
	}

	public static GoalConflictMessageDTO createInstance(GoalConflictMessage messageEntity, String nickname)
	{
		return new GoalConflictMessageDTO(messageEntity.getID(), messageEntity.getCreationTime(), nickname, messageEntity.getGoal().getName(),
				messageEntity.isUrlDisclosed() ? messageEntity.getURL() : null, messageEntity.getStatus());
	}

	@Component
	private static class Manager implements DTOManager
	{
		@Autowired
		private TheDTOManager theDTOFactory;

		@Autowired
		private BuddyService buddyService;

		@PostConstruct
		private void init()
		{
			theDTOFactory.addManager(GoalConflictMessage.class, this);
		}

		@Override
		public MessageDTO createInstance(UserDTO actingUser, Message messageEntity)
		{
			return GoalConflictMessageDTO.createInstance((GoalConflictMessage) messageEntity,
					getNickname(actingUser, (GoalConflictMessage) messageEntity));
		}

		@Override
		public MessageActionDTO handleAction(UserDTO actingUser, Message messageEntity, String action,
				MessageActionDTO requestPayload)
		{
			switch (action)
			{
				case REQUEST_DISCLOSURE:
					return handleAction_RequestDisclosure(actingUser, (GoalConflictMessage) messageEntity, requestPayload);
				default:
					throw GoalServiceException.actionNotSupported(action);
			}
		}

		private MessageActionDTO handleAction_RequestDisclosure(UserDTO actingUser, GoalConflictMessage messageEntity,
				MessageActionDTO requestPayload)
		{
			messageEntity.setStatus(Status.DISCLOSE_REQUESTED);
			GoalConflictMessage.getRepository().save(messageEntity);

			MessageDestination messageDestination = UserAnonymized.getRepository().findOne(messageEntity.getRelatedVPNLoginID())
					.getAnonymousDestination();
			messageDestination.send(DiscloseRequestMessage.createInstance(actingUser.getID(),
					actingUser.getPrivateData().getVpnProfile().getVPNLoginID(), actingUser.getPrivateData().getNickname(),
					requestPayload.getProperty("message"), messageEntity));
			MessageDestination.getRepository().save(messageDestination);

			return new MessageActionDTO(Collections.singletonMap("status", "done"));
		}

		private String getNickname(UserDTO actingUser, GoalConflictMessage messageEntity)
		{
			UUID vpnLoginID = messageEntity.getRelatedVPNLoginID();
			if (actingUser.getPrivateData().getVpnProfile().getVPNLoginID().equals(vpnLoginID))
			{
				return SELF_NICKNAME;
			}

			Set<BuddyDTO> buddies = buddyService.getBuddies(actingUser.getPrivateData().getBuddyIDs());
			for (BuddyDTO buddy : buddies)
			{
				if (vpnLoginID.equals(buddy.getVPNLoginID()))
				{
					return buddy.getNickname();
				}
			}
			throw new IllegalStateException("Cannot find buddy for VPN login ID '" + vpnLoginID + "'");
		}
	}
}
