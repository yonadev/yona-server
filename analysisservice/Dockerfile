FROM anapsix/alpine-java

ADD buildoutput /

RUN unzip build/distributions/analysisservice.zip \
  && rm build/distributions/analysisservice.zip

WORKDIR /analysisservice

EXPOSE 8080

CMD ["./bin/analysisservice"]
