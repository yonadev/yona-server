/*******************************************************************************
 * Copyright (c) 2015 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License, v.
 * 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server.entities;

import java.io.Serializable;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.support.Repositories;

public class RepositoryProvider implements ApplicationContextAware
{
    private static Repositories repositories;

    @SuppressWarnings("unchecked")
    public static <E, K extends Serializable> CrudRepository<E, K> getRepository(Class<E> entityClass, Class<K> keyClass)
    {
        return (CrudRepository<E, K>) repositories.getRepositoryFor(entityClass);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException
    {
        repositories = new Repositories(applicationContext);
    }
}
