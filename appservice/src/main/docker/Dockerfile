FROM java:8

WORKDIR /opt/appservice

EXPOSE 8080

VOLUME /tmp

ADD appservice.jar app.jar

RUN bash -c 'touch /app.jar'
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","app.jar"]
