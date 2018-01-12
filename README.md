# mod-circulation

Copyright (C) 2017 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

## Goal

FOLIO compatible circulation capabilities, including loan items from the inventory.

## Prerequisites

### Required

- Java 8 JDK
- Maven 3.5.0
- Implementation of the loan-storage interface e.g. [FOLIO Loan Storage Module](https://github.com/folio-org/mod-loan-storage)

### Optional

- Python 3 (for un-registering module during managed demo and tests via Okapi)
- Node.js 6.4 (for API linting)
- NPM 3.10 (for API linting)
- Gradle 3.3 (previous build mechanism)

## Preparation

### Git Submodules

There are some common RAML definitions that are shared between FOLIO projects via Git submodules.

To initialise these please run `git submodule init && git submodule update` in the root directory.

If these are not initialised, the inventory-storage module will fail to build correctly, and other operations may also fail.

More information is available on the [developer site](http://dev.folio.org/doc/setup#update-git-submodules).

## Common activities

### Running a general build

In order to run a general build (including the default tests), run `gradle`.

### Creating the circulation module JAR

In order to build an executable Jar (e.g. for Okapi to deploy), run `gradle fatJar`.

### Running the tests

#### Using fake modules

In order to run the tests, using a fake loan storage module, run ./quick-test.sh.

#### Using real modules (via Okapi)

In order to run the tests against a real storage module, run ./test-via-okapi.sh.

This requires [Okapi](https://github.com/folio-org/okapi) to be running and the relevant modules to be registered with it.

The test script will create a tenant and activate the modules for that tenant.

In order to change the specific versions of these dependencies, edit the test-via-okapi.sh script.

### Checking the RAML and JSON.Schema definitions

run `./lint.sh` to validate the RAML and JSON.Schema descriptions of the API (requires node.js and NPM)

## Design Notes

### Loan Rules Caching

The loan rules engine used for applying loan rules has an internal, local cache which is refreshed every 5 seconds.

This is per module instance, and so may result in different responses during this window after the loan rules are changed.

### Item Status

During the circulation process an item can change between a variety of states,
below is a table describing the most common states defined at the moment.

| Name | Description |
|---|---| 
| Available | This item is available to be lent to a patron |
| Checked out | This item is currently checked out to a patron |
| Checked out - Recalled | This item is currently checked out to a patron, another patron has requested it be returned as soon as possible |
| Checked out - Held | This item is currently checked out to a patron, another patron has requested it be held upon it’s return |

### Storing Information from Other Records

In order to facilitate the searching and sorting of requests by the properties of related records, a snapshot of some properties are stored with the request.

This snapshot is updated during POST or PUT requests by requesting the current state of those records.
It is possible for them to become out of sync with the referenced records.

the request JSON.schema uses the readOnly property to indicate that these properties, from the perspective of the client, are read only.

#### Properties Stored

##### Requesting User (referenced by requesterId, held in requester property)

* firstName
* lastName
* middleName
* barcode

##### Requested Item (referenced by itemId, held in item property)

* title
* barcode

### Including Properties From Other Records

In order to reduce the amount of requests a client needs to make, some properties from other records in responses.

#### Loans

Loans include information from the item, including locations. 
As this requires a second set of requests after the items have been fetched, responses may take longer than other requests.

## Additional Information

Other [modules](http://dev.folio.org/source-code/#server-side).

See project [CIRC](https://issues.folio.org/browse/CIRC)
at the [FOLIO issue tracker](http://dev.folio.org/community/guide-issues).

Other FOLIO Developer documentation is at [dev.folio.org](http://dev.folio.org/)

