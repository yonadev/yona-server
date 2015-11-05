/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.entities;

import java.io.Serializable;

import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.support.Repositories;

public class RepositoryProvider implements ApplicationListener<ContextRefreshedEvent> {
	private static Repositories repositories;

	@Override
	public void onApplicationEvent(ContextRefreshedEvent event) {
		repositories = new Repositories(event.getApplicationContext());
	}

	@SuppressWarnings("unchecked")
	public static <E, K extends Serializable> CrudRepository<E, K> getRepository(Class<E> entityClass,
			Class<K> keyClass) {
		return (CrudRepository<E, K>) repositories.getRepositoryFor(entityClass);
	}
}
