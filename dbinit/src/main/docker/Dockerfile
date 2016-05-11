FROM yonadev/hsqldb:2.3.2

ADD DatabaseInitializer.jar DatabaseInitializer.jar
ADD data data

RUN ["java","-jar","DatabaseInitializer.jar","--spring.jpa.hibernate.ddl-auto=update","--spring.batch.initializer.enabled=true","--spring.datasource.url=jdbc:hsqldb:file:thedatabase"]
