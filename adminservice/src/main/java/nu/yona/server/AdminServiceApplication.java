/*******************************************************************************
 * Copyright (c) 2015, 2016 Stichting Yona Foundation This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *******************************************************************************/
package nu.yona.server;

import javax.persistence.EntityManagerFactory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.batch.BatchAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.web.WebMvcAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import nu.yona.server.admin.batch.config.MainConfiguration;

@SpringBootApplication(scanBasePackages = { "nu.yona.server" }, exclude = { BatchAutoConfiguration.class,
		DataSourceAutoConfiguration.class, WebMvcAutoConfiguration.class })
@EnableCaching
@Import(MainConfiguration.class)
public class AdminServiceApplication
{
	public static void main(String[] args)
	{
		SpringApplication.run(AdminServiceApplication.class, args);
	}

	@Bean
	@Primary
	public PlatformTransactionManager txManager(EntityManagerFactory entityManagerFactory)
	{
		// This transaction manager replaces the one set by Spring Batch Admin
		// (org.springframework.jdbc.datasource.DataSourceTransactionManager).
		// With the DataSourceTransactionManager, JPA updates were not persisted.
		// TODO: Verify wither the Spring Batch Admin functionality still works.
		JpaTransactionManager jpaTransactionManager = new JpaTransactionManager();
		jpaTransactionManager.setEntityManagerFactory(entityManagerFactory);

		return jpaTransactionManager;
	}
}
