FROM openjdk:11-jre-slim

WORKDIR /opt/app

EXPOSE 8080 9080

VOLUME /tmp

RUN mkdir -p config/dummy # Workaround for spring-projects/spring-boot#26627
ADD appservice.jar app.jar

RUN sh -c 'touch /app.jar'

ENTRYPOINT ["java","-Djava.security.egd=file:/dev/urandom","-jar","app.jar", "--management.server.port=9080"]
