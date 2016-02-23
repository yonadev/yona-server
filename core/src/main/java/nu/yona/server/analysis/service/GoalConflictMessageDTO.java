/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

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
import nu.yona.server.messaging.entities.DiscloseRequestMessage;
import nu.yona.server.messaging.entities.Message;
import nu.yona.server.messaging.service.MessageActionDTO;
import nu.yona.server.messaging.service.MessageDTO;
import nu.yona.server.messaging.service.MessageDestinationDTO;
import nu.yona.server.messaging.service.MessageService;
import nu.yona.server.messaging.service.MessageService.DTOManager;
import nu.yona.server.messaging.service.MessageService.TheDTOManager;
import nu.yona.server.messaging.service.MessageServiceException;
import nu.yona.server.subscriptions.service.BuddyDTO;
import nu.yona.server.subscriptions.service.BuddyService;
import nu.yona.server.subscriptions.service.UserAnonymizedService;
import nu.yona.server.subscriptions.service.UserDTO;

@JsonRootName("goalConflictMessage")
public class GoalConflictMessageDTO extends MessageDTO
{
	private static final String SELF_NICKNAME = "<self>";
	private static final String REQUEST_DISCLOSURE = "requestDisclosure";
	private final String nickname;
	private final String activityCategoryName;
	private final String url;
	private Status status;
	private final Date activityStartTime;
	private final Date activityEndTime;

	private GoalConflictMessageDTO(UUID id, Date creationTime, String nickname, String activityCategoryName, String url,
			Status status, Date activityStartTime, Date activityEndTime)
	{
		super(id, creationTime);
		this.nickname = nickname;
		this.activityCategoryName = activityCategoryName;
		this.url = url;
		this.status = status;
		this.activityStartTime = activityStartTime;
		this.activityEndTime = activityEndTime;
	}

	@Override
	public String getType()
	{
		return "GoalConflictMessage";
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

	public String getActivityCategoryName()
	{
		return activityCategoryName;
	}

	public Status getStatus()
	{
		return status;
	}

	public String getUrl()
	{
		return url;
	}

	public Date getActivityStartTime()
	{
		return activityStartTime;
	}

	public Date getActivityEndTime()
	{
		return activityEndTime;
	}

	@Override
	public boolean canBeDeleted()
	{
		return true;
	}

	public static GoalConflictMessageDTO createInstance(GoalConflictMessage messageEntity, String nickname)
	{
		return new GoalConflictMessageDTO(messageEntity.getID(), messageEntity.getCreationTime(), nickname,
				messageEntity.getActivity().getGoal().getActivityCategory().getName(),
				messageEntity.isUrlDisclosed() ? messageEntity.getURL() : null, messageEntity.getStatus(),
				messageEntity.getActivity().getStartTime(), messageEntity.getActivity().getEndTime());
	}

	@Component
	private static class Manager implements DTOManager
	{
		@Autowired
		private TheDTOManager theDTOFactory;

		@Autowired
		private BuddyService buddyService;

		@Autowired
		private UserAnonymizedService userAnonymizedService;

		@Autowired
		private MessageService messageService;

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
					throw MessageServiceException.actionNotSupported(action);
			}
		}

		private MessageActionDTO handleAction_RequestDisclosure(UserDTO actingUser, GoalConflictMessage messageEntity,
				MessageActionDTO requestPayload)
		{
			messageEntity = updateMessageStatusAsDiscloseRequested(messageEntity);

			MessageDestinationDTO messageDestination = userAnonymizedService
					.getUserAnonymized(messageEntity.getRelatedUserAnonymizedID()).getAnonymousDestination();
			messageService.sendMessage(
					DiscloseRequestMessage.createInstance(actingUser.getID(), actingUser.getPrivateData().getUserAnonymizedID(),
							actingUser.getPrivateData().getNickname(), requestPayload.getProperty("message"), messageEntity),
					messageDestination);

			return MessageActionDTO.createInstanceActionDone(theDTOFactory.createInstance(actingUser, messageEntity));
		}

		private GoalConflictMessage updateMessageStatusAsDiscloseRequested(GoalConflictMessage messageEntity)
		{
			messageEntity.setStatus(Status.DISCLOSE_REQUESTED);
			return GoalConflictMessage.getRepository().save(messageEntity);
		}

		private String getNickname(UserDTO actingUser, GoalConflictMessage messageEntity)
		{
			UUID userAnonymizedID = messageEntity.getRelatedUserAnonymizedID();
			if (actingUser.getPrivateData().getUserAnonymizedID().equals(userAnonymizedID))
			{
				return SELF_NICKNAME;
			}

			Set<BuddyDTO> buddies = buddyService.getBuddies(actingUser.getPrivateData().getBuddyIDs());
			for (BuddyDTO buddy : buddies)
			{
				if (userAnonymizedID.equals(buddy.getUserAnonymizedID()))
				{
					return buddy.getNickname();
				}
			}
			throw new IllegalStateException("Cannot find buddy for user anonymized ID '" + userAnonymizedID + "'");
		}
	}
}
