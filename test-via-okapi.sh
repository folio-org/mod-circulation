#!/usr/bin/env bash

okapi_proxy_address="http://localhost:9130"
tenant_id="test_tenant"
circulation_direct_address=http://localhost:9605
circulation_instance_id=localhost-9605
circulation_module_id="circulation-4.5.0-SNAPSHOT"

#Needs to be the specific version of Inventory Storage you want to use for testing
inventory_storage_module_id="inventory-storage-5.1.1-SNAPSHOT"

#Needs to be the specific version of Circulation Storage you want to use for testing
circulation_storage_module_id="circulation-storage-3.2.1-SNAPSHOT"

echo "Check if Okapi is contactable"
curl -w '\n' -X GET -D -   \
     "${okapi_proxy_address}/_/env" || exit 1

echo "Create ${tenant_id} tenant"
./create-tenant.sh ${tenant_id}

echo "Activate circulation storage for ${tenant_id}"
activate_circulation_storage_json=$(cat ./activate.json)
activate_circulation_storage_json="${activate_circulation_storage_json/moduleidhere/$circulation_storage_module_id}"

curl -w '\n' -X POST -D - \
     -H "Content-type: application/json" \
     -d "${activate_circulation_storage_json}"  \
     "${okapi_proxy_address}/_/proxy/tenants/${tenant_id}/modules"

echo "Activate inventory storage for ${tenant_id}"
activate_inventory_storage_json=$(cat ./activate.json)
activate_inventory_storage_json="${activate_inventory_storage_json/moduleidhere/$inventory_storage_module_id}"

curl -w '\n' -X POST -D - \
     -H "Content-type: application/json" \
     -d "${activate_inventory_storage_json}"  \
     "${okapi_proxy_address}/_/proxy/tenants/${tenant_id}/modules"

gradle generateDescriptors

echo "Register circulation module"
./register.sh ${circulation_direct_address} ${circulation_instance_id} ${tenant_id}

./okapi-registration/unmanaged-deployment/register.sh \
  ${circulation_direct_address} \
  ${circulation_instance_id} \
  ${circulation_module_id} \
  ${okapi_proxy_address} \
  ${tenant_id}

echo "Run API tests"
gradle clean cleanTest testApiViaOkapi

test_results=$?

echo "Unregister circulation module"
./okapi-registration/unmanaged-deployment/unregister.sh \
  ${circulation_instance_id} \
  ${circulation_module_id} \
  ${tenant_id}

echo "Deactivate circulation storage for ${tenant_id}"
curl -X DELETE -D - -w '\n' "${okapi_proxy_address}/_/proxy/tenants/${tenant_id}/modules/${circulation_storage_module_id}"

echo "Deactivate inventory storage for ${tenant_id}"
curl -X DELETE -D - -w '\n' "${okapi_proxy_address}/_/proxy/tenants/${tenant_id}/modules/${inventory_storage_module_id}"

echo "Deleting ${tenant_id}"
./delete-tenant.sh ${tenant_id}

echo "Need to manually remove test_tenant storage as Tenant API no longer invoked on deactivation"

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
