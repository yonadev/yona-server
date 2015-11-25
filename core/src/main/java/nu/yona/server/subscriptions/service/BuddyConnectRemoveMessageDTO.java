package nu.yona.server.subscriptions.service;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.messaging.entities.Message;
import nu.yona.server.messaging.service.MessageActionDTO;
import nu.yona.server.messaging.service.MessageDTO;
import nu.yona.server.messaging.service.MessageService.DTOManager;
import nu.yona.server.messaging.service.MessageService.TheDTOManager;
import nu.yona.server.subscriptions.entities.BuddyConnectRemoveMessage;
import nu.yona.server.subscriptions.entities.User;
import nu.yona.server.subscriptions.service.BuddyService.DropBuddyReason;

@JsonRootName("buddyConnectRemoveMessage")
public class BuddyConnectRemoveMessageDTO extends BuddyMessageDTO
{
	private static final String PROCESS = "process";
	private DropBuddyReason reason;
	private boolean isProcessed;

	private BuddyConnectRemoveMessageDTO(UUID id, UserDTO user, UUID loginID, String nickname, String message,
			DropBuddyReason reason, boolean isProcessed)
	{
		super(id, user, nickname, message);
		this.reason = reason;
		this.isProcessed = isProcessed;
	}

	public DropBuddyReason getReason()
	{
		return reason;
	}

	public boolean isProcessed()
	{
		return isProcessed;
	}

	@Override
	public Set<String> getPossibleActions()
	{
		Set<String> possibleActions = new HashSet<>();
		if (!isProcessed)
		{
			possibleActions.add(PROCESS);
		}
		return possibleActions;
	}

	public static BuddyConnectRemoveMessageDTO createInstance(UserDTO requestingUser, BuddyConnectRemoveMessage messageEntity)
	{
		User userEntity = messageEntity.getUser(); // may be null if deleted
		UserDTO user = userEntity != null ? UserDTO.createInstance(userEntity) : UserDTO.createRemovedUserInstance();
		return new BuddyConnectRemoveMessageDTO(messageEntity.getID(), user, messageEntity.getRelatedLoginID(),
				messageEntity.getNickname(), messageEntity.getMessage(), messageEntity.getReason(), messageEntity.isProcessed());
	}

	@Component
	private static class Factory implements DTOManager
	{
		@Autowired
		private TheDTOManager theDTOFactory;

		@Autowired
		private BuddyService buddyService;

		@PostConstruct
		private void init()
		{
			theDTOFactory.addManager(BuddyConnectRemoveMessage.class, this);
		}

		@Override
		public MessageDTO createInstance(UserDTO actingUser, Message messageEntity)
		{
			return BuddyConnectRemoveMessageDTO.createInstance(actingUser, (BuddyConnectRemoveMessage) messageEntity);
		}

		@Override
		public MessageActionDTO handleAction(UserDTO actingUser, Message messageEntity, String action,
				MessageActionDTO requestPayload)
		{
			switch (action)
			{
				case PROCESS:
					return handleAction_Process(actingUser, (BuddyConnectRemoveMessage) messageEntity, requestPayload);
				default:
					throw new IllegalArgumentException("Action '" + action + "' is not supported");
			}
		}

		private MessageActionDTO handleAction_Process(UserDTO actingUser, BuddyConnectRemoveMessage messageEntity,
				MessageActionDTO requestPayload)
		{
			buddyService.removeBuddyAfterBuddyRemovedConnection(actingUser.getID(), messageEntity.getRelatedLoginID());

			return new MessageActionDTO(Collections.singletonMap("status", "done"));
		}
	}
}
