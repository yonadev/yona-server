FROM anapsix/alpine-java

ADD buildoutput /

RUN unzip build/distributions/adminservice.zip \
  && rm build/distributions/adminservice.zip

WORKDIR /adminservice

EXPOSE 8080

CMD ["./bin/adminservice"]
