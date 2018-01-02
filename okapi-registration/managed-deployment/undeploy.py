import requests
import sys
import json

def find_module_id():
    with open('target/ModuleDescriptor.json') as descriptor_file:
        return json.load(descriptor_file)['id']

args = sys.argv

module_id = find_module_id()

if(len(args) >= 2):
    tenant_id = args[1]
    okapi_address = args[2] or 'http://localhost:9130'
else:
    sys.stderr.write('Tenant ID must be passed on the command line')
    sys.exit()

url = '{0}/_/discovery/modules/{1}'.format(okapi_address, module_id)

instances_response = requests.get(url)

if(instances_response.status_code == 200):
    instance_ids = list(map(lambda instance: instance['instId'], instances_response.json()))

    for instance_id in instance_ids:
        instance_url = '{0}/_/discovery/modules/{1}/{2}'.format(okapi_address, module_id, instance_id)
        delete_response = requests.delete(instance_url)
        print('Delete Response for {0}: {1}'.format(instance_url, delete_response.status_code))

else:
    print('Could not enumerate instances of module {0}, status: {1}'.format(module_id, instances_response.status_code))
