# mod-circulation

Copyright (C) 2017-2023 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

## Documentation

Further documentation about this module can be found in the [/doc/](doc) folder:

* [Guide](doc/developer-guide.md) - introduction for developers
* [Operations guide](doc/operations-guide.md) - introduction for operations
* [Circulationrules](doc/circulationrules.md) - how the circulation rules file and the circulation rules engine work

## Goal

FOLIO compatible circulation capabilities, including loan items from the inventory.

## Prerequisites

### Required

- Java 11 JDK
- Maven 3.5.0
- Implementations of the interfaces described in the [module descriptor](descriptors/ModuleDescriptor-template.json)

### Optional

- Python 3 (for un-registering module during managed demo and tests via Okapi, and the lint-raml tools)

### Environmental

mod-circulation defines many `module permissions` as it integrates with many other parts of FOLIO.

These require the use of `Okapi 3.x` or later and an implementation of the `_tenantPermissions 1.1` interface.

## Preparation

### Git Submodules

There are some common RAML definitions that are shared between FOLIO projects via Git submodules.

To initialise these please run `git submodule init && git submodule update` in the root directory.

If these are not initialised, the inventory-storage module will fail to build correctly, and other operations may also fail.

More information is available on the [developer site](https://dev.folio.org/guides/developer-setup/#update-git-submodules).

## Common activities

### Running a general build

In order to run a general build (including the default tests), run `mvn test`.

### Creating the circulation module JAR

In order to build an executable Jar (e.g. for Okapi to deploy), run `mvn package`.

### Running the tests

#### Using fake modules

In order to run the tests, using a fake loan storage module, run ./quick-test.sh.

#### Using real modules (via Okapi)

In order to run the tests against a real storage module, run ./test-via-okapi.sh.

This requires [Okapi](https://github.com/folio-org/okapi) to be running and the relevant modules to be registered with it.

The test script will create a tenant and activate the modules for that tenant.

In order to change the specific versions of these dependencies, edit the test-via-okapi.sh script.

### Checking the RAML and JSON.Schema definitions

Follow the [guide](https://dev.folio.org/guides/raml-cop/) to use raml-cop to assess RAML, schema, and examples.

## Port

When running the jar file the module looks for the `http.port` and `port`
system property variables in this order, and uses the default 9801 as fallback. Example:

`java -Dhttp.port=8081 -jar target/mod-circulation.jar`

The Docker container exposes port 9801.

## Environment variables

Module integrates with Kafka in order to consume/publish domain events. This integration can
be configured using the following environment variables:

| Variable name      | Default value     |
|--------------------|-------------------|
| KAFKA_HOST         | localhost         |
| KAFKA_PORT         | 9092              |
| REPLICATION FACTOR | 1                 |
| MAX_REQUEST_SIZE   | 4000000           |
| ENV                | folio             |
| OKAPI_URL          | http://okapi:9130 |

If a variable is not present, its default values is used as a fallback. If this configuration is
invalid, the module will start, but Kafka integration will not work.

Module supports so-called floating collections but the feature is disabled by default. Floating
collections support can be switched on by setting the environment variable ENABLE_FLOATING_COLLECTIONS to TRUE.

| Variable name               | Default value     |
|-----------------------------|-------------------|
| ENABLE_FLOATING_COLLECTIONS | FALSE             |


## Design Notes

### Known Limitations

#### Requests Created out of Request Date Order

Requests are assigned a position based upon when they were created.
This means the requests could be in a different position in the queue than what
the request date suggests. We could re-order to queue based upon request date
each time it is changed, however this would impede the future requirement
for the ability to reorder the queue manually.

#### Creating an already closed loan

It is not possible to create a loan that is already closed via POST
due to checks that are performed during this request.
However it can be done when creating a loan in a specific location via PUT

#### API Tests

As it is intended that API tests can be run against real module instances,
some scenarios are not easily replicated without breaking this capability.

For example, previously there were tests that verified that some of the APIs
could handle missing inventory records. As mod-inventory-storage does not allow
used holdings or instance records to be deleted, these tests were removed.

These tests need to be replaced by tests that use specialised implementations
of the storage interfaces, separate to the fakes used for general API tests.

#### Tenant-selected timezone and locale support

* Tenant-selected timezone is supported only for check-out and renewal (and not for overridden renewals or request creation)
* Date formatting for patron notices does not respect tenant-selected locale

#### Patron notices

Patron notices are only implemented for check-in and check-out


### Check Out By Barcode

In additional to the typical loan creation API, it is possible to check out an item to a loanee (optionally via a proxy), using barcodes.

#### Example Request

```
POST http://{okapi-location}/circulation/check-out-by-barcode
{
    "itemBarcode": "036000291452",
    "userBarcode": "5694596854",
    "loanDate": "2018-03-18T11:43:54.000Z"
}
```

#### Example Success Response

```
HTTP/1.1 201 Created
content-type: application/json; charset=utf-8
content-length: 1095
location: /circulation/loans/e01ed4d3-28c4-4f9b-89a2-e818e0a6e7f5

{
    "id": "e01ed4d3-28c4-4f9b-89a2-e818e0a6e7f5",
    "status": {
        "name": "Open"
    },
    "action": "checkedout",
    "loanDate": "2018-03-18T11:43:54.000Z",
    "userId": "2f400401-a751-456a-9f57-415cbce65864",
    "itemId": "8722fa77-dc6b-4182-9ea0-e3b708bee0f5",
    "dueDate": "2018-04-08T11:43:54.000Z",
    "loanPolicyId": "af30cbee-5d54-4a83-842b-eaef0f02cfbe",
    "metadata": {
        "createdDate": "2018-04-25T18:17:49.545Z",
        "createdByUserId": "79ff2a8b-d9c3-5b39-ad4a-0a84025ab085",
        "updatedDate": "2018-04-25T18:17:49.545Z",
        "updatedByUserId": "79ff2a8b-d9c3-5b39-ad4a-0a84025ab085"
    },
    "item": {
        "title": "The Long Way to a Small, Angry Planet",
        "contributors": [
            {
                "name": "Chambers, Becky"
            }
        ],
        "barcode": "036000291452",
        "holdingsRecordId": "e2309cd0-5b99-4bf2-8620-df7ac8edd38a",
        "instanceId": "ffe94513-fd4a-4c17-86ae-bfc936b47c06",
        "callNumber": "123456",
        "status": {
            "name": "Checked out"
        },
        "location": {
            "name": "3rd Floor"
        },
        "materialType": {
            "name": "Book"
        }
    }
}
```

#### Example Failure Response

Below is an example of a failure response.

The message explains the reason for the refusal of the request.

The parameters refer to what part of the request caused the request to be refused.

```
HTTP/1.1 422 Unprocessable Entity
content-type: application/json; charset=utf-8
content-length: 200

{
    "errors": [
        {
            "message": "Cannot check out item via proxy when relationship is invalid",
            "parameters": [
                {
                    "key": "proxyUserBarcode",
                    "value": "6430530304"
                }
            ]
        }
    ]
}
```

#### Validation

Below is a short summary summary of most of the validation checks performed when using this endpoint.

Each includes an example of the error message provided and the parameter key included with the error.

|Check|Example Message|Parameter Key|Notes|
|---|---|---|---|
|Item does not exist|No item with barcode 036000291452 exists|itemBarcode| |
|Holding does not exist| | |otherwise it is not possible to lookup circulation rules|
|Item is already checked out|Item is already checked out|itemBarcode| |
|Existing open loan for item|Cannot check out item that already has an open loan|itemBarcode| |
|Proxy relationship is valid|Cannot check out item via proxy when relationship is invalid| |only if proxying|
|User must be requesting user|User checking out must be requester awaiting pickup|userBarcode|if there is an outstanding fulfillable request for item|
|User does not exist|Could not find user with matching barcode|userBarcode| |
|User needs to be active and not expired|Cannot check out to inactive user|userBarcode| |
|Proxy user needs to be active and not expired|Cannot check out via inactive proxying user|proxyUserBarcode|only if proxying|

### Renew By Barcode

It is possible to renew an item to a loanee (optionally via a proxy), using barcodes for the item and loanee.

#### Example Request

```
POST http://localhost:9605/circulation/renew-by-barcode
{
    "itemBarcode": "036000291452",
    "userBarcode": "5694596854"
}
```

#### Example Success Response

```
HTTP/1.1 200 OK
content-type: application/json; charset=utf-8
content-length: 1114
location: /circulation/loans/a2494e15-cecf-4f68-a5bf-701389b278ed

{
    "id": "a2494e15-cecf-4f68-a5bf-701389b278ed",
    "status": {
        "name": "Open"
    },
    "action": "renewed",
    "loanDate": "2018-03-04T11:43:54.000Z",
    "userId": "891fa646-a46e-4152-9989-efe3b0311e04",
    "itemId": "9edc9877-df4c-4dd8-9306-3f1d1444c3f8",
    "dueDate": "2018-03-31T23:59:59.000Z",
    "loanPolicyId": "750fd537-3438-4f06-b854-94a1c34d199d",
    "renewalCount": 1,
    "metadata": {
        "createdDate": "2018-06-08T13:35:39.097Z",
        "createdByUserId": "79ff2a8b-d9c3-5b39-ad4a-0a84025ab085",
        "updatedDate": "2018-06-08T13:35:39.162Z",
        "updatedByUserId": "79ff2a8b-d9c3-5b39-ad4a-0a84025ab085"
    },
    "item": {
        "holdingsRecordId": "092c24f3-44e8-44a5-9389-5b3f50e4895a",
        "instanceId": "a1039600-2076-4248-af75-6b561fdb0f09",
        "title": "The Long Way to a Small, Angry Planet",
        "barcode": "036000291452",
        "contributors": [
            {
                "name": "Chambers, Becky"
            }
        ],
        "callNumber": "123456",
        "status": {
            "name": "Checked out"
        },
        "location": {
            "name": "3rd Floor"
        },
        "materialType": {
            "name": "Book"
        }
    }
}
```

#### Example Failure Response

Below is an example of a failure response.

The message explains the reason for the refusal of the request.

The parameters refer to what part of the request caused the request to be refused.

```
HTTP/1.1 422 Unprocessable Entity
content-type: application/json; charset=utf-8
content-length: 611

{
    "errors": [
        {
            "message": "renewal at this time would not change the due date",
            "parameters": [
                {
                    "key": "loanPolicyName",
                    "value": "Limited Renewals And Limited Due Date Policy"
                },
                {
                    "key": "loanPolicyId",
                    "value": "9b28ec73-0582-4751-bd5c-65c03965ae65"
                }
            ]
        },
        {
            "message": "loan has reached it's maximum number of renewals",
            "parameters": [
                {
                    "key": "loanPolicyName",
                    "value": "Limited Renewals And Limited Due Date Policy"
                },
                {
                    "key": "loanPolicyId",
                    "value": "9b28ec73-0582-4751-bd5c-65c03965ae65"
                }
            ]
        }
    ]
}
```

### Circulation Rules Caching

The circulation rules engine used for applying circulation rules has an internal, local cache which is refreshed every 5 seconds and when a PUT to /circulation/rules changes the circulation rules.

This is per module instance, and so may result in different responses during this window after the circulation rules are changed.

### Circulation Rules

[doc/circulationrules.md](doc/circulationrules.md)

That document explains how the circulation rules engine calculates the loan policy (that specifies the loan period)
based on the patron's patron group and the item's material type, loan type, and location.

### Item Status

During the circulation process an item can change between a variety of states,
below is a table describing the most common states defined at the moment.

| Name | Description |
|---|---|
| Available | This item is available to be lent to a patron |
| Checked out | This item is currently checked out to a patron |
| Awaiting pickup | This item is awaiting pickup by a patron who has a request at the top of the queue|

### Request Status

| Name | Description |
|---|---|
| Open - Not yet filled | The requested item is not yet available to the requesting user |
| Open - Awaiting pickup | The item is available to the requesting user |
| Closed - Filled | |

### Storing Information from Other Records

In order to facilitate the searching and sorting of requests by the properties of related records, a snapshot of some
properties are stored with the request.

This snapshot is updated during POST or PUT requests by requesting the current state of those records.
It is possible for them to become out of sync with the referenced records.

the request JSON.schema uses the readOnly property to indicate that these properties, from the perspective of the client, are read only.

#### Properties Stored

##### Requesting User (referenced by requesterId, held in requester property)

* firstName
* lastName
* middleName
* barcode

##### Proxy Requesting User (referenced by proxyUserId, held in proxy property)

* firstName
* lastName
* middleName
* barcode

##### Requested Item (referenced by itemId, held in item property)

* title
* barcode

### Including Properties From Other Records

In order to reduce the amount of requests a client needs to make, some properties from other records in responses.

As this inclusion requires a chain of requests after the loans or requests have been fetched, the responses may take longer than other requests.

#### Loans

Loans include information from the item, including locations, holdingsRecordId and instanceId.

#### Requests

Requests include information from the item, including holdingsRecordId and instanceId.

#### Hold shelf clearance report

To create hold expiration report that can be used by staff to clear expired and cancelled holds from the shelf and put them back into circulation.
Then generate a report based on this logic:
* Find all of the items that are `Awaiting pickup`
* Choose the item’s which either have an empty request queue or request queue doesn't contain request with status `Open - Awaiting pickup`
* Select the request for each item that expired or was cancelled on the hold shelf most recently (by ranking the closed requests by a date used only for this purpose)
* Choose only the requests where the `pickup service point` matches the chosen service point

The API for generating a report is based on the presence of the 'awaitingPickupRequestClosedDate' property for the request JSONB.
Such behavior is required by database trigger for request update in the mod-circulation-storage
See [CIRCSTORE-127](https://issues.folio.org/browse/CIRCSTORE-127)

Data involved in the formation of the report:
* Requester name: lastName, firstName
* Requester barcode
* Item title
* Item barcode
* Call number
* Request status
* Hold shelf expiration date: Date with timestamp

#### Example Request

```
GET http://{okapi-location}/circulation/requests-reports/hold-shelf-clearance/:{servicePointId}
```

#### Example Success Response

```
HTTP/1.1 200 Ok
content-type: application/json; charset=utf-8
content-length: 230

{
  "requests": [
    {
      "id": "f5cec279-0da6-4b44-a3df-f49b0903f325",
      "requestType": "Hold",
      "requestDate": "2017-08-05T11:43:23Z",
      "requesterId": "61d939e4-f2ae-4c53-95d2-224a802fa2a6",
      "itemId": "3e5d5433-a271-499c-94f4-5f3e4652e537",
      "fulfillmentPreference": "Hold Shelf",
      "requestExpirationDate": "2017-08-31T22:25:37Z",
      "holdShelfExpirationDate": "2017-09-01T22:25:37Z",
      "position": 1,
      "status": "Closed - Pickup expired",
      "pickupServicePointId": "4ae438be-308a-468d-a815-ad109c288f05",
      "awaitingPickupRequestClosedDate": "2019-03-11T15:45:23.000+0000"
      "item": {
        "title": "Children of Time",
        "barcode": "760932543816",
        "callNumber": "A344JUI"
      },
      "requester": {
        "firstName": "Stephen",
        "lastName": "Jones",
        "middleName": "Anthony",
        "barcode": "567023127436"
      }
    }
  ],
  "totalRecords": 1
}
```
### Configuration setting for CheckoutLock Feature
  To enable this feature for a tenant, we need to add the below configuration in mod-settings. See https://issues.folio.org/browse/UXPROD-3515 to know more about this feature.

#### Permissions
  To make a post call to mod-settings, user should have below permissions.
```
  mod-settings.entries.item.post
  mod-settings.global.write.mod-circulation
```

#### Example request
```
POST https://{okapi-location}/settings/entries
  {
    "id":"1e01066d-4bee-4cf7-926c-ba2c9c6c0001",
    "scope": "mod-circulation",
    "key":"checkoutLockFeature",
    "value":"{\"checkOutLockFeatureEnabled\":true,\"noOfRetryAttempts\":25,\"retryInterval\":250,\"lockTtl\":2500}"
}
```

| parameter | Type        | Description                                                                                           |
|---------|-------------|-------------------------------------------------------------------------------------------------------|
| `id`    | UUID        | id should be provided of type UUID.                                                                   |
| `scope` | String      | Scope should be the module name. Here, it will be "mod-circulation"                                   |
| `key`   | String      | Key should be feature name which we are enabling the settings. Here, it will be "checkoutLockFeature" |
| `value` | Json Object | Settings for checkout lock feature                                                                    |


| Value options                | Type    | Description                                                                                                                                                                                    |
|------------------------------|---------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `checkOutLockFeatureEnabled` | boolean | Indicates whether or not to enable this feature for the tenant. Default value is false(disabled).                                                                                              |
| `noOfRetryAttempts`          | int     | The maximum number of times to retry the lock ackquiring process during checkout. Once the retry is exhausted, system will return error. Default value is 30.                                  |
| `retryInterval`              | int | The amount of time to wait between retries in milliseconds. Default value is 250.                                                                                                              |
| `lockTtl`                    | int | Maximum amount of time(milliseconds) that the lock should exist for a patron. After this time, the lock will gets deleted and lock will be provided for another request. Default value is 3000 |

## Additional Information

Other [modules](https://dev.folio.org/source-code/#server-side).

Other FOLIO Developer documentation is at [dev.folio.org](https://dev.folio.org/)

### Issue tracker

See project [CIRC](https://issues.folio.org/browse/CIRC)
at the [FOLIO issue tracker](https://dev.folio.org/guidelines/issue-tracker/).

### ModuleDescriptor

See the built `target/ModuleDescriptor.json` for the interfaces that this module
requires and provides, the permissions, and the additional module metadata.

### API documentation

This module's [API documentation](https://dev.folio.org/reference/api/#mod-circulation).

### Code analysis

[SonarQube analysis](https://sonarcloud.io/dashboard?id=org.folio%3Amod-circulation).

### Download and configuration

The built artifacts for this module are available.
See [configuration](https://dev.folio.org/download/artifacts) for repository access,
and the [Docker image](https://hub.docker.com/r/folioorg/mod-circulation/).

