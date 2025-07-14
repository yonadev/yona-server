FROM yonadev/mariadb-liquibase-update:4.3.5

RUN apk update && apk add --no-cache openldap-clients && \
    rm -rf /var/lib/apt/lists/*

COPY changelogs /changelogs

COPY json /json

# Override with custom script for k8s / helm support
COPY update.sh /scripts/
RUN chmod +x /scripts/update.sh
CMD ["/scripts/update.sh", "/var/log/liquibase-update/liquibase-update.log"]
