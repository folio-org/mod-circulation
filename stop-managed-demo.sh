#!/usr/bin/env bash

instance_id=${1:-}
okapi_proxy_address=${2:-http://localhost:9130}

tenant_id="demo_tenant"
module_id="mod-circulation-5.0.0"

./okapi-registration/managed-deployment/unregister.sh \
  ${module_id} \
  ${okapi_proxy_address} \
  ${tenant_id}

./delete-tenant.sh
