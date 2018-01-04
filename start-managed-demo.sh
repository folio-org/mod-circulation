#!/usr/bin/env bash

okapi_proxy_address=${1:-http://localhost:9130}

tenant_id="demo_tenant"

echo "Check if Okapi is contactable"
curl -w '\n' -X GET -D -   \
     "${okapi_proxy_address}/_/env" || exit 1

echo "Package circulation module"
mvn clean package -q -Dmaven.test.skip=true || exit 1

./create-tenant.sh

./okapi-registration/managed-deployment/register.sh \
  ${okapi_proxy_address} \
  ${tenant_id}
