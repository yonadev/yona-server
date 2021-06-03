FROM yonadev/mariadb-liquibase-update:3.6.3

# Add jq (to validate json)
RUN mkdir -p config/dummy # Workaround for spring-projects/spring-boot#26627
ADD https://github.com/stedolan/jq/releases/download/jq-1.5/jq-linux64 /usr/local/bin/jq
RUN chmod +x /usr/local/bin/jq

RUN apt-get update && apt-get install -y \
    ldap-utils --no-install-recommends && rm -rf /var/lib/apt/lists/*

COPY changelogs /changelogs

# Override with custom script for k8s / helm support
COPY update.sh /scripts/
RUN chmod +x /scripts/update.sh

