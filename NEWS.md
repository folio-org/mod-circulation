## 11.0.0 2018-08-02

* No longer sets item status to variants of `Checked out` (CIRC-126)
* Introduce request `position` property (CIRC-83)
* Introduce endpoint for fetching request queue for an item (CIRC-83)
* Request queue positions are allocated upon creation, closure, cancellation or deletion (CIRC-83)
* Closed requests cannot be replaced via PUT (CIRC-122)
* Fixes response code discrepancy between implementation and description of renewal API in `circulation` interface (CIRC-130)
* Provides `circulation` interface 3.5 (CIRC-83)
* Requires `request-storage` interface 2.2 (CIRC-83)

## 10.7.0 2018-07-10

* Provide different error messages when item not found in different circumstances (CIRC-123)
* Determine location using permanent and temporary locations from both the holdings and item (CIRC-121)
* Includes `location` for `requests` (CIRC-121)
* Provides `circulation` interface 3.4 (CIRC-121)
* Requires `item-storage` interface 5.3 (CIRC-121)
* Requires `holdings-storage` interface 1.3 (CIRC-121)

## 10.6.0 2018-06-28

* Initial renewal API for renewing an item to a loanee using IDs (CIRC-117)
* Provides `circulation` interface 3.4 (CIRC-117)

## 10.5.0 2018-06-27

* Add support for 'Cancelled - Closed' request status (CIRC-118)
* Add support for 'cancellationReason' and 'cancellationDate' request fields (CIRC-90)
* Changes validation message when attempting to check out to user other than requester to include more information (CIRC-114)
* Provides `circulation` interface 3.3 (CIRC-90)
* Requires `request-storage` interface 2.1 (CIRC-90, CIRC-118)

## 10.4.0 2018-06-18

* Initial renewal API for renewing an item to a loanee using their barcodes (CIRC-100)
* Provides `circulation` interface 3.2 (CIRC-100)

## 10.3.0 2018-06-05

* Support `users` interface 15.0 which removes `meta` property from proxy relationships (CIRC-113)

## 10.2.1 2018-05-31

* Fixed due date schedule limits are applied for rolling loan policies during check out (CIRC-106)
* Check out requests using a rolling loan policy with missing policy definition respond with an error message (CIRC-108)
* Proxy validation uses properties from either root or meta object whilst transitioning between models (CIRC-107)
* Reports an error if the loan rules match to a non-existent policy (CIRC-111)

## 10.1.2 2018-05-01

* Initial check out API for checking out an item to a loanee using their barcodes (CIRC-74)
* Rename `metaData` property to `metadata` (CIRC-98)
* Add shelving location to loan rule parser (CIRC-16)
* Add required priority keyword to loan rule engine (CIRC-17)
* Add "all" keyword to loan rule engine (CIRC-18)
* Add ! (negation) operator to loan rule engine (CIRC-19)
* Implement applyAll API endpoint of loan rules engine (CIRC-33)
* applyAll API endpoint returns loan policy ID rather than loan policy itself (CIRC-33)
* Implement /circulation/loan-rules/apply-all endpoint (fix 500 status) (CIRC-63)
* Implement antlr parser; wire loan rules against UUIDs of controlled vocabularies (CIRC-35)
* Fix "Loan rule is not processed when no space after colon between rule and policy" (CIRC-73)
* Fix for multiple value headers in storage module responses (CIRC-103)
* Adds missing location permission for getting loans in module descriptor (CIRC-105)
* Forward on X-Okapi-Request-Id header if present (CIRC-99)
* Provides `circulation` interface 3.1 (CIRC-98, CIRC-33, CIRC-74)
* Requires `loan-storage` interface 4.0 (CIRC-98)
* Requires `request-storage` interface 2.0 (CIRC-98)
* Requires `loan-policy-storage` interface 1.2 (CIRC-74)
* Requires `fixed-due-date-schedules-storage` interface 2.0 (CIRC-74)

## 9.0.1 2018-04-16

* Loan rules `apply` endpoint missing or malformed reports query parameters (CIRC-95)
* Provides `circulation` interface 2.13 (CIRC-95)
* Requires `locations` interface 1.1 or 2.0 (CIRC-91)
* No longer requires `shelf-locations` interface (CIRC-91)

## 8.1.1 2018-04-09

* Includes proxy user's name for requests (CIRC-88)
* Stores proxy user's name for requests (so it can be used for sorting, CIRC-88)
* Defaults request `status` to `Open - Not yet filled` (CIRC-53)
* Updates request `status` for hold shelf delivery to `Open - Awaiting pickup` on check in (CIRC-53)
* Updates request `status` for hold shelf delivery to `Closed - Filled` on check out to the requester (CIRC-53)
* Disallow checking out item to other patrons when request is awaiting pickup (CIRC-53)
* Refuse loan creation for already checked out items (CIRC-53)
* Refuse loan creation when item or holding does not exist (CIRC-53)
* Only allow `Open` and `Closed` loan status (may become interface constraint in future, CIRC-53)
* Item status is determined by the oldest request in the request queue (CIRC-52)
* Allows un-expiring relationships when validating proxy (CIRC-92)
* Reuse Vert.x HTTP client within the circulation verticle (to allow for connection pooling, CIRC-86)
* Use == relation when finding related records (CIRC-87)
* Validation messages are structurally similar to schema (CIRC-93)
* Provides circulation interface 2.12 (CIRC-88)
* Requires request-storage interface 1.5 (CIRC-88)

## 7.5.0 2018-03-13

* Adds `status` property to `requests` (CIRC-53)
* Adds `proxyUserId` to `requests` (CIRC-77)
* Adds `systemReturnDate` property to `loans` (CIRC-81)
* Adds `status` property to `requests` (CIRC-53)
* Adds `materialType` property to the `item` for `loans` (CIRC-80)
* Adds `callNumber` property to the `item` for `loans` (CIRC-80)
* Adds `contributors` array to the `item` for `loans` (CIRC-80)
* Validates proxy relationship when creating or updating `loans` and `requests` (CIRC-79)
* Provides circulation interface 2.11 (CIRC-53, CIRC-81, CIRC-80, CIRC-77, CIRC-79)
* Requires request-storage interface 1.4 (CIRC-53, CIRC-77, CIRC-79)
* Requires users interface version 14.2 (CIRC-79)
* Requires loan-storage interface 3.5 (CIRC-81)
* Requires material-types interface 2.0 (CIRC-80)

## 7.1.1 2018-02-12

* Adds `holdingsRecordId` and `instanceId` properties to the item for a loan (CIRC-61)
* Adds `holdingsRecordId` and `instanceId` properties to the item for a request (CIRC-70)
* Request to PUT loan rules will respond with 422 when invalid (CIRC-68)
* Accept comments in loan rules without a space (CIRC-69)
* Adds missing `deliveryAddressTypeId` into the request schema definition (CIRC-71)
* Provides circulation interface 2.7 (CIRC-61, CIRC-70)

## 7.0.1 2018-02-06

* Applies loan rules to determine the policy to use whilst handling loan requests (CIRC-51)
* Adds `loanPolicyId` property to a loan, to keep the last policy that was applied to the loan (CIRC-51)
* Clear cache of loan rules engine when changing loan rules (CIRC-59)
* Default loan status to `Open` (and action to `checkedout`) if not provided (CIRC-60)
* Provides circulation interface 2.6 (CIRC-51)
* Requires loan-storage interface version 3.4 (CIRC-51)

## 6.0.0 2018-01-08

* Requires item-storage interface version 4.0 or 5.0 (CIRC-57)
* Requires instance-storage interface version 3.3 or 4.0 (CIRC-57)
* Requires holdings-storage interface version 1.0 (CIRC-57)

## 5.0.0 2017-12-20

* Allow multiple requests for the same item (CIRC-54)
* Use permanent location from holding or item (CIRC-49)
* Use title from instance or item (CIRC-50)
* Use item permanent and temporary locations for `location` property in loans (CIRC-36)
* Item status `Checked Out` is now `Checked out` (CIRC-39)
* Creating or changing a loan updates the `itemStatus` snapshot (similar to `action`) in loan storage (CIRC-38)
* Creating a hold or recall request changes the associated item's `status` (CIRC-39)
* Creating a hold or recall request updates the `action` and `itemStatus` snapshots
for an open loan for the same item in storage, in order to create a loan history entry (CIRC-40, CIRC-38)
* A hold or recall request will be rejected when associated item's `status` is not checked out (CIRC-39)
* A request for an item that does not exist will be rejected (CIRC-39)
* The `itemStatus` snapshot is not included in loan representation, as the current status
is included from the item, and having both may be confusing (CIRC-38)
* Requires loan-storage interface version 3.3 (CIRC-38)
* Requires item-storage interface version 4.0 (CIRC-36)
* Requires holdings-storage interface version 1.0 (CIRC-49, CIRC-50)
* Requires shelf-locations interface version 1.0 (CIRC-36)

## 4.5.0 2017-10-11

* Introduces `circulation/requests` for making requests for items (CIRC-27)
* Stores item and requesting user metadata with request, in order to aid searching / sorting (CIRC-28, CIRC-29)
* Put loan rules validation error message into JSON (CIRC-34)
* Provides circulation interface version 2.5
* Requires request-storage interface version 1.1
* Requires users interface version 14.0
* Adds mod- prefix to names of the built artifacts (FOLIO-813)

## 4.4.0 2017-09-01

* Introduces `/circulation/loan-rules` for getting and replacing loan rules (CIRC-11)
* Introduces `/circulation/loan-rules/apply` for loan rules engine (CIRC-26)
* Provides circulation interface version 2.4
* Requires loan-rules-storage interface version 1.0 (new dependency)
* Remove `module.scan.enabled`, storage and configuration permissions from circulation.all set, as part of moving permissions to UI modules (CIRC-32)
* Generates Descriptors at build time from templates in ./descriptors (FOLIO-701)

## 4.3.0 2017-08-17

* Introduces proxy user ID for a loan (CIRC-23)
* Adds `metaData` property to loan (for created and updated information, CIRC-24)
* Requires loan-storage interface version 3.2
* Provides circulation interface version 2.3

## 4.2.0 2017-08-15

* Include item status and location in loans
* Provides circulation interface version 2.2

## 4.1.0 2017-08-01

* Adds property `dueDate` to loan
* Adds `renewalCount` property to loan
* Provides circulation interface version 2.1
* Requires loan-storage interface version 3.1
* Include implementation version in `id` in Module Descriptor
* Includes missing `action` property definition in loan schema

## 4.0.0 2017-07-17

* Adds required property `action` to loan
* Provides circulation interface version 2.0
* Requires loan-storage interface version 3.0

## 3.0.0 2017-06-07

* Removes item representation from requests forwarded to storage
* Circulation.all permission set includes permissions for related UI tasks
* Requires loan-storage interface version 2.0
* Requires item-storage interface version 3.0

## 2.1.0 2017-05-30

* Makes the all circulation permissions set visible (requires Okapi 1.3.0)
* Includes permission definition for enabling the scan UI module (included in all permissions set)

## 2.0.0 2017-04-25

* Requires item-storage interface version 2.0 (no incompatible changes)

## 1.0.0 2017-04-04

* Required permissions for requests

## 0.1.0 2017-04-02

* Initial release
