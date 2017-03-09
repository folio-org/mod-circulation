#!/usr/bin/env bash

okapi_proxy_address="http://localhost:9130"
tenant_id="test_tenant"

echo "Check if Okapi is contactable"
curl -w '\n' -X GET -D -   \
     "${okapi_proxy_address}/_/env" || exit 1

echo "Create ${tenant_id} tenant"
./create-tenant.sh ${tenant_id}

echo "Activate loan storage for ${tenant_id}"
activate_json=$(cat ./activate-loan-storage.json)

curl -w '\n' -X POST -D - \
     -H "Content-type: application/json" \
     -d "${activate_json}"  \
     "${okapi_proxy_address}/_/proxy/tenants/${tenant_id}/modules"

echo "Run API tests"
gradle clean testApiViaOkapi

test_results=$?

echo "Deactivate loan storage for ${tenant_id}"
curl -X DELETE -D - -w '\n' "${okapi_proxy_address}/_/proxy/tenants/${tenant_id}/modules/loan-storage"

echo "Deleting ${tenant_id}"
./delete-tenant.sh ${tenant_id}

if [ $test_results != 0 ]; then
    echo '--------------------------------------'
    echo 'BUILD FAILED'
    echo '--------------------------------------'
    exit 1;
else
    echo '--------------------------------------'
    echo 'BUILD SUCCEEDED'
    echo '--------------------------------------'
    exit 1;
fi
