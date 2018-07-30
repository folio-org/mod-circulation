#!/usr/bin/env bash

okapi_proxy_address=${1:-http://localhost:9130}

tenant_id="demo_tenant"

echo "Checking if Okapi is contactable"
curl -w '\n' -X GET -D -   \
     "${okapi_proxy_address}/_/env" || exit 1

echo "Packaging circulation module"
mvn clean package -q -Dmaven.test.skip=true || exit 1

echo "Creating demo tenant"
./create-tenant.sh

echo "Registering module with Okapi"
./okapi-registration/managed-deployment/register.sh \
  ${okapi_proxy_address} \
  ${tenant_id}
