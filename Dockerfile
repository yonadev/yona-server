FROM anapsix/alpine-java

ADD buildoutput /

RUN unzip build/distributions/yona-server.zip \
  && rm build/distributions/yona-server.zip

WORKDIR /yona-server

EXPOSE 8080

CMD ["./bin/yona-server"]
