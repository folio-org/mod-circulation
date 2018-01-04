#!/usr/bin/env bash

okapi_proxy_address=${1:-http://localhost:9130}
tenant_id="demo_tenant"

./okapi-registration/managed-deployment/unregister.sh \
  ${okapi_proxy_address} \
  ${tenant_id}
