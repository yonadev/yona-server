/*******************************************************************************
 * Copyright (c) 2015, 2017 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAnonymizedRepository extends JpaRepository<UserAnonymized, UUID>
{
	int countByLastMonitoredActivityDateBetween(LocalDate startDate, LocalDate endDate);

	int countByLastMonitoredActivityDateIsNull();
}
