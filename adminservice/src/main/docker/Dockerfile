FROM openjdk:11-jre-slim

WORKDIR /opt/app

EXPOSE 8080 9080

VOLUME /tmp

ADD adminservice.jar app.jar

RUN sh -c 'touch /app.jar'

ENTRYPOINT ["java","-Djava.security.egd=file:/dev/urandom","-jar","app.jar", "--management.server.port=9080", "--spring.config.location=optional:classpath:/,optional:classpath:/config/,optional:file:./,optional:file:./config/"]
