#!/usr/bin/env bash

module_id=${1}
okapi_proxy_address=${2:-http://localhost:9130}
tenant_id=${3:-demo_tenant}

curl -X DELETE -D - -w '\n' "${okapi_proxy_address}/_/proxy/tenants/${tenant_id}/modules/${module_id}"
curl -X DELETE -D - -w '\n' "${okapi_proxy_address}/_/proxy/modules/${module_id}"

if which python3
then
  echo "Undeploying managed module instances from Okapi using Python"

  pip3 install requests

  script_directory="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

  python3 ${script_directory}/undeploy.py ${module_id} ${tenant_id} ${okapi_proxy_address}

else
  echo "Install Python3 to undeploy managed module from Okapi automatically"
fi
