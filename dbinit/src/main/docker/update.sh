#!/bin/bash
#*******************************************************************************
# Copyright (c) 2017, 2018 Stichting Yona Foundation
#
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.
#*******************************************************************************

# Note - We are running under K8S as an init job.
# If we exit non zero we will be rescheduled until success

ERROR_EXIT_CODE=1
GIT_BASE="https://raw.githubusercontent.com/yonadev/yona-server/"
MAX_TRIES=${MAX_TRIES:-5}

test_ldap_seeded () {
  #Test for SSL Group
  /usr/bin/ldapsearch -H ${LDAP_URL} -x -D "${LDAP_USER_DN}" -w "${LDAP_USER_PASSWORD}" -b "${LDAP_DN}" "(ou=SSL)" | grep -q "numEntries: 1"
  return $?
}

apply_ldap_seed () {
 
  if [ -z ${LDAP_URL+x} ]; then
    echo "No LDAP_URL Environment variable - Not seeding ldap"
    return 0
  fi

  #Test for SSL Group
  test_ldap_seeded
  if [ $? -eq 0 ]; then
    echo "LDAP SSL Group already present - skipping"
    return 0
  fi

  #Add SSL Group
  echo "LDAP SSL Group not found - Adding"
  /usr/bin/ldapadd -H ${LDAP_URL} -x -D "${LDAP_USER_DN}" -w "${LDAP_USER_PASSWORD}" << EOFLDAP
version: 1

dn: ou=SSL,${LDAP_DN}
objectClass: groupOfNames
objectClass: top
cn: SSL
member: ${LDAP_DN}
ou: SSL
EOFLDAP
  if [ $? -eq 1 ]; then
    echo "Failed to execute the LDAP SSL Group Add - Bailing"
    return 1
  fi

  #Test for SSL Group
  test_ldap_seeded
  if [ $? -eq 0 ]; then
    echo "LDAP SSL Group correctly seeded"
    return 0
  fi

  return 1
}

apply_json () {
  SOURCE="${1}"
  TARGET="${2}"
  upload_validate $SOURCE $TARGET
  if [ $? -eq 1 ]; then
    echo "Failed to upload JSON to ${TARGET}"
    return 1
  fi
}

upload_validate () {
  SOURCE="${1}"
  TARGET="${2}"
  COUNT=1
  while [  $COUNT -le $MAX_TRIES ]; do
    echo  "Attempting to push ${TARGET}: attempt $COUNT of $MAX_TRIES"
    STATUS=$(curl -s -o "/tmp/response" -w '%{http_code}' -X PUT "${TARGET}" -d @${SOURCE} --header "Content-Type: application/json")
    if [ $STATUS == "200" ]; then
      echo "Post Applied ${TARGET} - Result Code ${STATUS}"
      cat /tmp/response
      return 0
    fi
    echo "Failed to post ${TARGET} - Error Code ${STATUS}"
    cat /tmp/response
    sleep 5
    let COUNT=COUNT+1
  done
  return 1
}

apply_liquibase () {
  # Todo ? is missing /changelogs fatal ?  Will there always be a /changelogs perhaps empty ?
  [ -d /changelogs ] || (echo "Folder /changelogs/ does not exist" ; return 1)
  cd /changelogs
  # Todo ? having no contents fatal ?
  CHANGE_LOG=`echo changelog.*`
  [ -f "$CHANGE_LOG" ] || (echo "Cannot find a single change log matching /changelogs/changelog.*" ; return 1)

  echo "Applying changelogs ..."
  COUNT=1
  while [ "$COUNT" -le "$MAX_TRIES" ]; do
     echo  "Attempting to apply changelogs: attempt $COUNT of $MAX_TRIES"
     liquibase --logLevel=info --changeLogFile=$CHANGE_LOG update
     if [ $? -eq 0 ];then
        echo "Changelogs successfully applied"
        if [ -n "${RELEASE}" ]; then
          echo "Tagging with ${RELEASE}"
          liquibase tag ${RELEASE}
        fi
        return 0
     fi
     echo "Failed to apply changelogs"
     sleep 2
     let COUNT=COUNT+1
  done
  echo "Too many failed attempts"
  return 1
}

#Main 

{
echo `date` *** Start ${0}
# Todo ? is missing /changelogs fatal ?  Will there always be a /changelogs perhaps empty ?
[ -d /changelogs ] || (echo "Folder /changelogs/ does not exist" ; exit $ERROR_EXIT_CODE)
cd /changelogs

echo "Setting up liquibase"
: ${USER?"USER not set"}
: ${PASSWORD?"PASSWORD not set"}
: ${URL?"URL not set"}
cat <<CONF > liquibase.properties
  driver: org.mariadb.jdbc.Driver
  classpath:/opt/jdbc_drivers/$DRIVER_JAR
  url: $URL
  username: $USER
  password: $PASSWORD
CONF

# Apply Liquibase
apply_liquibase
if [ $? -eq 1 ]; then
  echo "Failed to apply Liquibase Changes - Exiting"
  exit $ERROR_EXIT_CODE
fi

if [ -n "${RELEASE}" ]; then
  # Apply Quartz Jobs
  echo "Applying Quartz Jobs"
  apply_json "/json/QuartzOtherJobs.json" "http://batch.${SERVICE_NAMESPACE}.svc.cluster.local:8080/scheduler/jobs/OTHER/"
  if [ $? -eq 1 ]; then
    exit $ERROR_EXIT_CODE
  fi

  # Apply Quartz Triggers (requires RELEASE env to be set)
  echo "Applying Quartz Triggers"
  apply_json "/json/QuartzOtherCronTriggers.json" "http://batch.${SERVICE_NAMESPACE}.svc.cluster.local:8080/scheduler/triggers/cron/OTHER/"
  if [ $? -eq 1 ]; then
    exit $ERROR_EXIT_CODE
  fi

  # Apply Categories
  echo "Applying Categories"
  apply_json /json/${ACT_CATEGORIES_JSON_FILE} "http://admin.${SERVICE_NAMESPACE}.svc.cluster.local:8080/activityCategories/"
  if [ $? -eq 1 ]; then
    exit $ERROR_EXIT_CODE
  fi

  # Apply LDAP Seed
  echo "Applying LDAP seed if needed"
  apply_ldap_seed
  if [ $? -eq 1 ]; then
    exit $ERROR_EXIT_CODE
  fi
else
  echo "RELEASE environment variable not set - Not running JSON updates"
fi

echo "All updates applied"
exit 0 # Job won't be rescheduled when exiting with 0
echo `date` *** End ${0}
} | tee -a ${1}
exit ${PIPESTATUS[0]}
