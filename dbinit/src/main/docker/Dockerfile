FROM yonadev/mariadb-liquibase-update:4.3.5

RUN apt-get update && apt-get install -y \
    ldap-utils --no-install-recommends && rm -rf /var/lib/apt/lists/*

COPY changelogs /changelogs

COPY json /json

# Override with custom script for k8s / helm support
COPY update.sh /scripts/
RUN chmod +x /scripts/update.sh
