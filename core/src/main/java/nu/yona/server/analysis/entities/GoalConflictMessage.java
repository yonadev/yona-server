/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.analysis.entities;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Transient;

import nu.yona.server.crypto.CryptoUtil;
import nu.yona.server.goals.entities.Goal;
import nu.yona.server.messaging.entities.DisclosureRequestMessage;
import nu.yona.server.messaging.entities.DisclosureResponseMessage;
import nu.yona.server.messaging.entities.Message;

@Entity
public class GoalConflictMessage extends Message
{
	public enum Status
	{
		ANNOUNCED, DISCLOSURE_REQUESTED, DISCLOSURE_ACCEPTED, DISCLOSURE_REJECTED
	}

	/**
	 * If this is a message sent to a buddy, this refers to the self goal conflict message posted at the user having the goal.
	 * Otherwise {@literal null}.
	 */
	@ManyToOne
	private GoalConflictMessage originGoalConflictMessage;

	@OneToMany(mappedBy = "originGoalConflictMessage", cascade = { CascadeType.REMOVE })
	private final List<GoalConflictMessage> buddyGoalConflictMessages;

	@ManyToOne
	private Activity activity;
	@ManyToOne
	private Goal goal;
	private Status status;

	@OneToMany(mappedBy = "disclosureRequestTargetGoalConflictMessage", cascade = { CascadeType.REMOVE })
	private final List<DisclosureRequestMessage> disclosureRequestMessages;

	@OneToMany(mappedBy = "disclosureResponseTargetGoalConflictMessage", cascade = { CascadeType.REMOVE })
	private final List<DisclosureResponseMessage> disclosureResponseMessages;

	@Transient
	private Optional<String> url;
	@Column(length = 3000)
	private byte[] urlCiphertext;

	// Default constructor is required for JPA
	public GoalConflictMessage()
	{
		this.buddyGoalConflictMessages = null;
		this.disclosureRequestMessages = null;
		this.disclosureResponseMessages = null;
	}

	public GoalConflictMessage(UUID relatedUserAnonymizedId, Activity activity, Goal goal, Optional<String> url, Status status)
	{
		super(relatedUserAnonymizedId);

		this.goal = goal;
		this.activity = activity;
		this.url = url;
		this.status = status;
		this.buddyGoalConflictMessages = new ArrayList<>();
		this.disclosureRequestMessages = new ArrayList<>();
		this.disclosureResponseMessages = new ArrayList<>();
	}

	public Activity getActivity()
	{
		return activity;
	}

	public Goal getGoal()
	{
		return goal;
	}

	public GoalConflictMessage getOriginGoalConflictMessage()
	{
		return originGoalConflictMessage;
	}

	private void setOriginGoalConflictMessage(GoalConflictMessage originGoalConflictMessage)
	{
		this.originGoalConflictMessage = originGoalConflictMessage;
	}

	public void addBuddyGoalConflictMessage(GoalConflictMessage goalConflictMessage)
	{
		buddyGoalConflictMessages.add(goalConflictMessage);
		goalConflictMessage.setOriginGoalConflictMessage(this);
	}

	public Optional<String> getUrl()
	{
		return url;
	}

	@Override
	public void encrypt()
	{
		urlCiphertext = CryptoUtil.encryptString(url.orElse(null));
	}

	@Override
	public void decrypt()
	{
		url = Optional.ofNullable(CryptoUtil.decryptString(urlCiphertext));
	}

	public boolean isFromBuddy()
	{
		return originGoalConflictMessage != null;
	}

	public Status getStatus()
	{
		return status;
	}

	public void setStatus(Status status)
	{
		this.status = status;
	}

	public void addDisclosureRequest(DisclosureRequestMessage disclosureRequestMessage)
	{
		disclosureRequestMessages.add(disclosureRequestMessage);
		disclosureRequestMessage.setTargetGoalConflictMessage(this);
	}

	public void addDisclosureResponse(DisclosureResponseMessage disclosureResponseMessage)
	{
		disclosureResponseMessages.add(disclosureResponseMessage);
		disclosureResponseMessage.setTargetGoalConflictMessage(this);
	}

	public static GoalConflictMessage createInstanceForBuddy(UUID relatedUserAnonymizedId, GoalConflictMessage origin)
	{
		if (origin == null)
		{
			throw new IllegalArgumentException("origin cannot be null");
		}

		GoalConflictMessage goalConflictMessage = new GoalConflictMessage(relatedUserAnonymizedId, origin.getActivity(),
				origin.getGoal(), origin.getUrl(), Status.ANNOUNCED);
		origin.addBuddyGoalConflictMessage(goalConflictMessage);
		return goalConflictMessage;
	}

	public static GoalConflictMessage createInstance(UUID relatedUserAnonymizedId, Activity activity, Goal goal,
			Optional<String> url)
	{
		return new GoalConflictMessage(relatedUserAnonymizedId, activity, goal, url, Status.ANNOUNCED);
	}

	public boolean isUrlDisclosed()
	{
		return !isFromBuddy() || getStatus() == Status.DISCLOSURE_ACCEPTED;
	}
}
