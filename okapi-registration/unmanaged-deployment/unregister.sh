#!/usr/bin/env bash

tenant_id=${1:-demo_tenant}
okapi_proxy_address=${2:-http://localhost:9130}

if which python3
then
  echo "Un-registering module from Okapi using Python"

  pip3 install requests

  script_directory="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

  python3 ${script_directory}/unregister.py ${tenant_id} ${okapi_proxy_address}

else
  echo "Install Python3 to un-register module from Okapi automatically"
fi
