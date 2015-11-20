FROM java:8

WORKDIR /opt/analysisservice

EXPOSE 8080

VOLUME /tmp

ADD analysisservice.jar app.jar

RUN bash -c 'touch /app.jar'
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","app.jar"]
