FROM anapsix/alpine-java:8

WORKDIR /opt/adminservice

EXPOSE 8080

VOLUME /tmp

ADD adminservice.jar app.jar

RUN bash -c 'touch /app.jar'
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","app.jar"]
