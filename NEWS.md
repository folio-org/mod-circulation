## 19.1.0 2020-10-09

* Ages overdue items to lost (CIRC-851, CIRC-852, CIRC-854, CIRC-877, CIRC-878, CIRC-889) 
* Refunds set cost fees for lost items (CIRC-716, CIRC-844, CIRC-845, CIRC-934)
* Records a note for the patron when a claimed returned item is resolved (CIRC-742)
* Overdue notices are sent until the item is returned or the patron is charged (CIRC-722)
* Restricts access to processes (e.g. check out, renewal) for aged to lost items (CIRC-668, CIRC-669, CIRC-670, CIRC-671, CIRC-672, CIRC-881)
* Renewals that do not change the due date can be overridden (CIRC-937)
* Publishes audit log events for check in and check out (CIRC-924)
* Publishes audit log events for sent notices (CIRC-929)
* Requires JDK 11 (CIRC-883)
* Provides `circulation 9.4`
* Requires `loan-storage 7.1`
* Requires `item-storage 8.5`
* Requires `notes 1.0`

## 19.0.0 2020-06-12

* Charges fees for lost items (CIRC-707, CIRC-715, CIRC-717, CIRC-743, CIRC-746, CIRC-747, CIRC-748, CIRC-749, CIRC-765)
* Refuses circulation operations based upon automated patron blocks (CIRC-755)
* Disallows policies that cannot be found from being referenced in rules (CIRC-658)
* Claimed returned items can be marked missing (CIRC-689)
* Withdrawn items can be checked in or checked out (CIRC-690, CIRC-692)
* Missing items can be checked out (CIRC-688)
* Withdrawn items cannot be requested (CIRC-704)
* Respects opening hours of primary service point when charging overdue fines (CIRC-736, CIRC-723)
* Doubles the recommended memory to support receiving and publishing messages (CIRC-762)
* HTTP requests timeout in 5 minutes (CIRC-710)
* Provides `declare-item-lost 0.2`
* Provides `change-due-date 0.1`
* Provides `claim-item-returned 0.2`
* Provides `circulation 9.3`
* Provides `circulation-event-handlers 0.1`
* Requires `loan-storage 7.0`
* Requires `item-storage 8.4`
* Requires `pubsub-event-types 0.1`
* Requires `pubsub-publishers 0.1`
* Requires `pubsub-subscribers 0.1`
* Requires `pubsub-publish 0.1`
* Requires `automated-patron-blocks 0.1`

## 18.0.0 2020-03-12

* Charges overdue fines according to policy (CIRC-524, CIRC-548, CIRC-591, CIRC-656, CIRC-677, CIRC-678, CIRC-680, CIRC-682)
* Can declare items lost (CIRC-567, CIRC-578, CIRC-614, CIRC-615, CIRC-616, CIRC-618, CIRC-625)
* Can claim an item has been returned (CIRC-626, CIRC-637, CIRC-638, CIRC-639, CIRC-645, CIRC-660)
* Blocks patrons from borrowing more items according to item limit policy (CIRC-558)
* Sends patron notices at the end of a check-in session (CIRC-559, CIRC-560, CIRC-568)
* Sends pickup reminder, hold shelf expiration and request expiration notices irrespective of order of notices set up in policy (CIRC-605, CIRC-606)
* Ignores or deletes invalid notices when sending patron notices (CIRC-582, CIRC-620)
* Informs the client when a check-in is considered in-house use (CIRC-622)
* Stores a record of each check in (CIRC-643, CIRC-665)
* Does not attempt to anonymize loans more than once (CIRC-666)
* Uses system date for when item was last checked in (CIRC-607)
* Can search for requests using item’s ISBN (CIRC-623)
* Includes effective call number in hold shelf report (CIRC-546)
* Includes extra properties in in-transit report (CIRC-556, CIRC-575)
* Provide item’s enumeration, chronology and volume for loans (CIRC-593)
* Uses Vert.x web client instead of HTTP client (CIRC-308)
* Upgrades to `Vert.x 3.8.4`
* Provides `circulation 9.2`
* Provides `circulation-rules 1.1`
* Provides `requests-reports 0.7`
* Provides `inventory-reports 0.4`
* Provides `pink-slips 0.2`
* Provides `request-move 0.7`
* Provides `declare-item-lost 0.1`
* Provides `claim-item-returned 0.1`
* Provides `patron-action-session 0.2`
* Requires `loan-storage 6.6`
* Requires `item-storage 8.1`
* Requires `request-storage 3.3`
* Requires `request-storage-batch 0.3`
* Requires `check-in-storage 0.2`

## 17.0.0 2019-12-02

* Introduces ability to reorder requests in an item’s queue (CIRC-446, CIRC-455, CIRC-531)
* Supports delivery fulfilment for requests (CIRC-454, CIRC-508, CIRC-509, CIRC-511)
* Can automatically anonymize closed loans (CIRC-364, CIRC-367)
* Prevents loan anonymization when loan has outstanding fees or fines (CIRC-368)
* Can specify an alternative loan or renewal period when item has a pending hold request (CIRC-199, CIRC-200)
* Stores last time an item is checked in (CIRC-522)
* Introduces lost item fee and overdue fine policies into circulation rules (CIRC-467, CIRC-495)
* Introduces location units into circulation rules (CIRC-405, CIRC-413, CIRC-414)
* Can block renewal when item has a pending hold request (CIRC-201)
* Support opening hours that cross midnight when checking out items (CIRC-534)
* Long term hold shelf requests at the end of the day (CIRC-543)
* Introduces check in / check out sessions (CIRC-431, CIRC-432, CIRC-433)
* Introduces ability to generate pick slips (CIRC-494)
* Sends patron notices when loan due date changed manually (CIRC-439)
* Introduces report for identifying in-transit items (CIRC-518, CIRC-519, CIRC-520, CIRC-569)
* Prevents inactive or blocked patron from placing requests (CIRC-445, CIRC-476)
* Introduces more tokens for staff slips (CIRC-428, CIRC-542)
* Reduces chance of request position collisions corrupting the request queue for an item (CIRC-463, CIRC-527)
* Does not send patron notices when check in does not change request status (CIRC-477)
* Changes container memory management (CIRC-565, FOLIO-2358)
* Provides `requests-reports 0.4`
* Provides `inventory-reports 0.2`
* Provides `pick-slips 0.1`
* Provides `request-move 0.4`
* Provides `loan-anonymization 0.1`
* Provides `circulation 8.3`
* Provides `circulation rules 1.0`
* Provides `patron-action-session 0.1`
* Requires `item-storage 7.8`
* Requires `holdings-storage 1.0, 2.0, 3.0 or 4.0`
* Requires `request-storage 3.2`
* Requires `request-storage-batch 0.2`
* Requires `calendar 3.0 or 4.0`
* Requires `patron-notice-policy-storage 0.11`
* Requires `location-units 2.0`
* Requires `patron-action-session-storage 0.1`

## 16.7.0 2019-09-18

* Only change due date for early return on first recall request (CIRC-440)

## 16.6.0 2019-09-09

* Loan is only updated during requests when due date changes (CIRC-289)
* Respect tenant timezone when creating or moving requests (CIRC-434, CIRC-443)
* Remembers patron group of borrower at check out (CIRC-327)
* Loan renewal responds with multiple failure reasons (CIRC-384)
* Sends time based request related patron notices (CIRC-387)
* Groups multiple item together for time based loan notices (CIRC-408)
* Can configure number of patron notices processed at a time (CIRC-407)
* Prevent requests from being moved above page requests (CIRC-416)
* Retain checked out status after requests are moved (CIRC-411, CIRC-429)
* Various permissions related fixes (CIRC-409, CIRC-418, CIRC-447)
* Includes correct `check in date` token for notices (CIRC-420)
* Protects against no loans policy after overridden check out (CIRC-424)
* Provides `circulation 7.11` (CIRC-327)
* Requires `patron-notice-policy-storage 0.7` (CIRC-387)
* Requires `location-units 1.1` (CIRC-418)

## 16.5.0 2019-07-24

* Send due date changed notice when moving recall request (CIRC-316)

## 16.4.0 2019-07-23

* Can override renewals with request related failures (CIRC-311, CIRC-319)
* Introduces experimental move request API (CIRC-315, CIRC-316, CIRC-333, CIRC-395)
* Decides upon item for title level requests based upon due date (CIRC-361)
* Provides template context for staff slips during check in (CIRC-378)
* Adds `Closed - Unfilled` and `Closed - Pickup expired` request states (CIRC-350)
* Includes additional location information for requests (CIRC-331)
* Includes outstanding fees and fines amount to pay for loans (CIRC-323)
* Assorted bug fixes (CIRC-305, CIRC-350, CIRC-353, CIRC-356, CIRC-357, CIRC-371, CIRC-390)
* Provides `circulation 7.10` (CIRC-323, CIRC-350, CIRC-315, CIRC-331)
* Provides `requests-move 0.2` (CIRC-350)
* Provides `requests-reports 0.2` (CIRC-350)
* Requires `calendar 3.0` (CIRC-363, MODCAL-45)
* Requires `feesfines 15.0` (CIRC-232)
* Requires `loan-storage 5.3 or 6.0` (CIRC-380)
* Requires `instance-storage 4.0, 5.0, 6.0 or 7.0` (CIRC-396)

## 16.3.0 2019-06-17

* Use sets for module permissions to reduce size when included in headers (CIRC-352)
* Only fetch single record by ID when ID is not null (CIRC-359)
* Only update request queue when loan is closed (CIRC-351)
* Uses correct request type for instance level requests (CIRC-344)

## 16.2.0 2019-06-12

* Processes scheduled patron notices (CIRC-337)
* Includes location related tokens for patron notices (CIRC-332)
* Handles missing request expiration date for instance level requests (CIRC-345)
* Does not attempt to store extended requester patron group information (CIRC-342)

## 16.1.0 2019-06-10

* Introduces creation of requests based upon an instance (CIRC-245, CIRC-264, CIRC-265, CIRC-267)
* Includes borrower personal information for loans (CIRC-290, CIRC-335, CIRC-336)
* Notifies requester for all recall requests, not only those which change due date (CIRC-295)
* Include additional tokens in patron notices (CIRC-296, CIRC-297)
* Schedule time based patron notices on check out (CIRC-322)
* Send scheduled patron notices (CIRC-310)
* Provides custom hold shelf clearance report (CIRC-320)
* Corrects issues with incorrect request JSON schema documentation (CIRC-321)
* Includes technical metadata in module descriptor (FOLIO-2003)
* Can only use drools 7.0.0 due to compatibility with Alpine Linux (CIRC-309)
* Provides `circulation 7.6` (CIRC-245, CIRC-265, CIRC-267)
* Provides `requests-reports 0.1` (CIRC-320)
* Provides `_timer 1.0 interface for sending scheduled patron notices` (CIRC-310)
* Requires `request-storage 3.1` (CIRC-320)
* Requires `cancellation-reason-storage 1.1` (CIRC-296, CIRC-297)
* Requires `loan-types 2.2` (CIRC-296, CIRC-297)
* Requires `scheduled-notice-storage 0.1` (CIRC-310)

## 16.0.0 2019-05-09

* Block loan renewal when item is recalled (CIRC-202)
* Can override loan policy when checking out an item (CIRC-211)
* Replacing a loan ignores the derived service point properties (CIRC-237)
* Better validation error message when trying to create a request for an item that does not exist (CIRC-241)
* Pickup service point is required for requests fulfilled to the hold shelf (CIRC-243)
* Can override renewal for items which are not loanable (CIRC-249)
* Do not attempt to fetch related records when no requests are found (CIRC-250)
* Clear loan action after overriding renewal (CIRC-251)
* Stop a patron having more than a single open request for an item (CIRC-255)
* Stop a patron from requesting an item they have on loan (CIRC-258)
* Send request related patron notices (CIRC-256, CIRC-262)
* Truncate loan due date when item is recalled (CIRC-259)
* Paged items can only be checked out by the requester (CIRC-260)
* Disallow overridden renewal if due date would not change (CIRC-261)
* Can check out an item with a barcode containing whitespace (CIRC-284)
* Allow requests for items on order or in process (CIRC-275)
* Provides `circulation 7.5`

## 15.0.0 2019-03-15

* Introduces endpoints to determine request and patron notice policies (CIRC-187, CIRC-196, CIRC-197)
* Due date calculation includes closed due date management (CIRC-158, CIRC-159, CIRC-160, CIRC-180, CIRC-186, CIRC-217, CIRC-206, CIRC-226)
* Respect chosen timezone when calculating due date during check out and renewal (CIRC-224, CIRC-238)
* Provides override mechanism for renewals (CIRC-174, CIRC-180, CIRC-212, CIRC-221)
* Send patron notices when item is checked out or checked in to return loan (CIRC-222)
* Introduces support for page requests (CIRC-189)
* Populates `hold shelf expiration date` when request begins `awaiting pickup` (CIRC-194)
* Requests only begin `awaiting pickup` when checked in to selected service point (CIRC-172, CIRC-193)
* Restricts when requests can be created based upon policy and item status whitelist (CIRC-207, CIRC-208, CIRC-230)
* Changes loan due date when recall request is created depending upon policy (CIRC-203)
* Prevents check out of missing items (CIRC-231)
* Improves validation of loans (CIRC-173)
* Adds `tags` to requests (CIRC-188, CIRC-232)
* Improves RAML documentation (CIRC-142, CIRC-220, CIRC-244)
* Increases HTTP client connection pool size from 5 to 100 connections (CIRC-225)
* Includes `copy number` from item in requests (CIRC-175)
* Uses Alpine docker image (CIRC-185)
* Provides `circulation 7.4`
* Requires `loan-storage 5.3`
* Requires `circulation-rules-storage 1.0`
* Requires `request-storage 3.0`
* Requires `loan-policy-storage 1.2 or 2.0`
* Requires `request-policy-storage 1.0`
* Requires `calendar 2.0`
* Requires `patron-notice-policy-storage 0.7`
* Requires `patron-notice 1.0`
* Requires `configuration 2.0`

## 14.1.0 2018-12-06

* Include item ID in check in API response when no loan is present (CIRC-176)

## 14.0.0 2018-11-30

* Provides support for basic in transit to home process during check in (CIRC-146)
* Includes extended check in and check out point properties in loans (CIRC-150)
* Includes extended destination service point properties in items when checking in (CIRC-146)
* Using PUT to a loan for check in is no longer supported (CIRC-146)
* Provides `circulation` interface version 5.2 (CIRC-146, CIRC-150)
* Requires only `item-storage` interface version `6.1` or `7.0` (CIRC-146)
* Requires only `location` interface version `3.0` (CIRC-146)

## 13.1.0 2018-11-28

* Initial check in by barcode API for checking in an item at a service point (CIRC-154)
* Removes additional delivery address properties before storage (CIRC-171)
* Provides `circulation` interface version 5.1 (CIRC-154)
* Requires `item-storage` interface version `5.3`, `6.0` or `7.0` (CIRC-170)
* Requires `instance-storage` interface version `4.0`, `5.0`, or  `6.0` (CIRC-168)
* Requires `holdings-storage` interface version `1.3`, `2.0` or `3.0` (CIRC-169)

## 13.0.0 2018-11-23

* Stores the service point where checking out or in occurred (CIRC-104)
* Uses RAML 1.0 for API documentation (CIRC-157)
* Include extended `patronGroup` properties for requesting `user` for (CIRC-156)
* Validate pickup service point for a request (CIRC-152)
* Bug fixes for fetching related records (CIRC-153, CIRC-161, CIRC-164, CIRC-165)
* Provides `circulation` 5.0 interface (CIRC-104)
* Requires `loan-storage` 5.2 interface (CIRC-104)
* Requires `request-storage` 2.3 interface (CIRC-147)
* Requires `service-points` 3.0 interface (CIRC-152)

## 12.1.0 2018-11-08

* Introduce request `pickupServicePointId` (CIRC-147)
* Include `contributorNames`, `enumeration`, `callNumber` and `status` of the `item` for requests (CIRC-140)
* Include `deliveryAddress` and `patronGroupId` of the `user` for requests (CIRC-140)
* Include `dueDate` of the `current loan` for requests (CIRC-140)
* Requires version 2.0 or 3.0 of `locations` (CIRC-143)
* Requires version 5.3 or 6.0 of `item-storage` (CIRC-141)
* Requires version 4.0 or 5.0 of `instance-storage` (CIRC-141)
* Requires version 1.3 or 2.0 of `holdings-storage` (CIRC-141)
* Provides `circulation` 4.2 interface (CIRC-141, CIRC-140)

# 12.0.0 2018-09-09

* Only requires `userId` for open loans (CIRC-136)
  * Although it is not possible to create already closed loans
* Provides `circulation` 4.0 interface (CIRC-136)
* Requires `loan-storage` interface 5.0 (CIRC-136)

## 11.0.1 2018-09-03

* Fix module permissions for request collection endpoint (CIRC-134)

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
