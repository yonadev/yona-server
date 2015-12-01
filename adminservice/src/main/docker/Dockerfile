FROM java:8

WORKDIR /opt/app

EXPOSE 8080

VOLUME /tmp

ADD adminservice.jar app.jar
ADD data data

RUN bash -c 'touch /app.jar'

ENTRYPOINT ["java","-Djava.security.egd=file:/dev/urandom","-jar","app.jar"]
