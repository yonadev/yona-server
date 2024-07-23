FROM eclipse-temurin:17.0.12_7-jre-alpine

WORKDIR /opt/app

EXPOSE 8080 9080

VOLUME /tmp

ADD analysisservice.jar app.jar

RUN sh -c 'touch /app.jar'

ENTRYPOINT ["java","-Djava.security.egd=file:/dev/urandom","-jar","app.jar", "--management.server.port=9080"]
