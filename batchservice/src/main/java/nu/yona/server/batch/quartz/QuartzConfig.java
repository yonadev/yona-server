package nu.yona.server.batch.quartz;

import javax.sql.DataSource;

import org.quartz.spi.JobFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

@Configuration
public class QuartzConfig
{
	@Bean
	public SpringBeanJobFactory springBeanJobFactory(ApplicationContext applicationContext)
	{
		AutowiringSpringBeanJobFactory jobFactory = new AutowiringSpringBeanJobFactory();
		jobFactory.setApplicationContext(applicationContext);
		return jobFactory;
	}

	@Bean
	public SchedulerFactoryBean schedulerFactoryBean(ApplicationContext applicationContext, DataSource dataSource,
			JobFactory jobFactory)
	{
		SchedulerFactoryBean schedulerFactory = new SchedulerFactoryBean();
		schedulerFactory.setConfigLocation(new ClassPathResource("quartz.properties"));
		schedulerFactory.setAutoStartup(true);
		schedulerFactory.setDataSource(dataSource);
		schedulerFactory.setJobFactory(jobFactory);
		return schedulerFactory;
	}
}