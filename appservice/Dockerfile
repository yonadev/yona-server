FROM anapsix/alpine-java

ADD buildoutput /

RUN unzip build/distributions/appservice.zip \
  && rm build/distributions/appservice.zip

WORKDIR /appservice

EXPOSE 8080

CMD ["./bin/appservice"]
