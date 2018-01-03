#!/usr/bin/env bash

okapi_proxy_address=${1:-http://localhost:9130}
tenant_id=${2:-demo_tenant}
deployment_descriptor_filename=${3:-target/DeploymentDescriptor.json}

deployment_json=$(cat ./${deployment_descriptor_filename})

curl -w '\n' -X POST -D -   \
     -H "Content-type: application/json"   \
     -d "${deployment_json}" \
     "${okapi_proxy_address}/_/deployment/modules"

curl -w '\n' -D - -s \
     -X POST \
     -H "Content-type: application/json" \
     -d @./target/ModuleDescriptor.json \
     "${okapi_proxy_address}/_/proxy/modules"

activate_json=$(cat ./target/activate.json)

curl -w '\n' -X POST -D - \
     -H "Content-type: application/json" \
     -d "${activate_json}" \
     "${okapi_proxy_address}/_/proxy/tenants/${tenant_id}/modules"
