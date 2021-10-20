#!/usr/bin/env bash

# Copyright (c) 2017 Cloudera, Inc. All rights reserved.

set -e
set -x

# Explicitly add RHEL5/6, SLES11/12 locations to path
export PATH=/usr/kerberos/bin:/usr/kerberos/sbin:/usr/lib/mit/sbin:/usr/sbin:/usr/lib/mit/bin:/usr/bin:$PATH

PRINC=$1

echo "ZZZ: Skipping deletion of principal $PRINC"
exit 0

# first, get ticket for CM principal
kinit -k -t $CMF_KEYTAB_FILE $CMF_PRINCIPAL

if [ -z "$KRB5_CONFIG" ]; then
  echo "Using system default krb5.conf path."
else
  echo "Using custom config path '$KRB5_CONFIG', contents below:"
  cat $KRB5_CONFIG
fi

set +e
ipa service-find $PRINC
ERR=$?
set -e

if [ $ERR -eq 0 ]; then
  IPA_HOST=$(ipa env server | tr -d '[:space:]' | cut -f2 -d:)
  HOST=`echo $PRINC | cut -d "/" -f 2 | cut -d "@" -f 1`
  if [[ "${PRINC:0:5}" = "HTTP/" && "$IPA_HOST" = "$HOST" ]] ; then
    echo "skipping deleting IPA HTTP principal: $PRINC"
  else
    echo "Deleting $PRINC from IPA."
    ipa service-del $PRINC
    if [ $? -ne 0 ]; then
      echo "Deletion of the account $PRINC failed."
      kdestroy
      exit 1
    fi
  fi
else
  echo "IPA account $PRINC not found. Nothing to delete."
fi

kdestroy
exit 0
