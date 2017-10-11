#!/usr/bin/env bash

okapi_proxy_address=${1:-http://localhost:9130}

tenant_id="demo_tenant"
deployment_descriptor="build/DeploymentDescriptor.json"

echo "Check if Okapi is contactable"
curl -w '\n' -X GET -D -   \
     "${okapi_proxy_address}/_/env" || exit 1

echo "Package circulation module"
gradle clean generateDescriptors fatJar

./create-tenant.sh

./okapi-registration/managed-deployment/register.sh \
  ${okapi_proxy_address} \
  ${tenant_id} \
  ${deployment_descriptor}
