/*******************************************************************************
 * Copyright (c) 2015, 2018 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.subscriptions.entities;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import javax.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID>
{
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from User u where u.id = :id")
	User findByIdForUpdate(@Param("id") UUID id);

	User findByMobileNumber(String mobileNumber);

	int countByAppLastOpenedDateBetween(LocalDate startDate, LocalDate endDate);

	int countByAppLastOpenedDateIsNull();

	int countByMobileNumberConfirmationCodeIsNull();

	int countByMobileNumberConfirmationCodeIsNotNull();

	int countByMobileNumberConfirmationCodeIsNotNullAndIsCreatedOnBuddyRequest(boolean isCreatedOnBuddyRequest);

	@Query("select u from User u where u.newDeviceRequest != null and u.newDeviceRequest.creationTime < :cuttOffDate")
	Set<User> findAllWithExpiredNewDeviceRequests(@Param("cuttOffDate") LocalDateTime cuttOffDate);

	@Query("select count(u) from User u where u.appLastOpenedDate != null and datediff(u.appLastOpenedDate, u.creationTime) >= :minNumberOfDays and datediff(u.appLastOpenedDate, u.creationTime) < :maxNumberOfDays")
	int countByNumberOfDaysAppOpenedAfterInstallation(int minNumberOfDays, int maxNumberOfDays);
}
