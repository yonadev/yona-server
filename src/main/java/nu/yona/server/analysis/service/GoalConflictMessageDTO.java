/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.service;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonRootName;

import nu.yona.server.analysis.model.GoalConflictMessage;
import nu.yona.server.messaging.entities.Message;
import nu.yona.server.messaging.service.MessageActionDTO;
import nu.yona.server.messaging.service.MessageDTO;
import nu.yona.server.messaging.service.MessageService.DTOFactory;
import nu.yona.server.messaging.service.MessageService.TheDTOFactory;
import nu.yona.server.subscriptions.entities.Buddy;
import nu.yona.server.subscriptions.entities.User;

@JsonRootName("goalConflictMessage")
public class GoalConflictMessageDTO extends MessageDTO {
	private final String nickname;
	private final String goalName;
	private final String url;

	private GoalConflictMessageDTO(UUID id, String nickname, String goalName, String url) {
		super(id);
		this.nickname = nickname;
		this.goalName = goalName;
		this.url = url;
	}

	public String getNickname() {
		return nickname;
	}

	public String getGoalName() {
		return goalName;
	}

	public String getUrl() {
		return url;
	}

	public static GoalConflictMessageDTO createInstance(User actingUserEntity, GoalConflictMessage messageEntity) {
		return new GoalConflictMessageDTO(messageEntity.getID(),
				getNickname(actingUserEntity, messageEntity.getAccessorID()), messageEntity.getGoal().getName(),
				messageEntity.getURL());
	}

	private static String getNickname(User actingUserEntity, UUID accessorID) {
		if (actingUserEntity.getAccessorID().equals(accessorID)) {
			return "<self>";
		}

		Set<Buddy> buddies = actingUserEntity.getBuddies();
		for (Buddy buddy : buddies) {
			if (accessorID.equals(buddy.getAccessorID())) {
				return buddy.getNickName();
			}
		}
		throw new IllegalStateException("Cannot find buddy for accessor ID '" + accessorID + "'");
	}

	@JsonIgnore
	@Override
	public Set<String> getPossibleActions() {
		return new HashSet<>();
	}

	@Override
	public MessageActionDTO handleAction(User requestingUserEntity, String action, MessageActionDTO payload) {
		throw new IllegalArgumentException("Action '" + action + "' is not supported");
	}

	@Component
	private static class Factory implements DTOFactory {
		@Autowired
		private TheDTOFactory theDTOFactory;

		@PostConstruct
		private void init() {
			theDTOFactory.addFactory(GoalConflictMessage.class, this);
		}

		@Override
		public MessageDTO createInstance(User actingUserEntity, Message messageEntity) {
			return GoalConflictMessageDTO.createInstance(actingUserEntity, (GoalConflictMessage) messageEntity);
		}
	}
}
