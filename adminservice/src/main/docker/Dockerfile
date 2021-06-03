FROM openjdk:11-jre-slim

WORKDIR /opt/app

EXPOSE 8080 9080

VOLUME /tmp

ADD adminservice.jar app.jar

RUN sh -c 'touch /app.jar'

# Workaround for spring-projects/spring-boot#26627
ENV SPRING_CONFIG_LOCATION=optional:classpath:/,optional:classpath:/config/,optional:file:./,optional:file:./config/
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/urandom","-jar","app.jar", "--management.server.port=9080"]
