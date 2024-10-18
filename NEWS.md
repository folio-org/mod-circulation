## 24.3.0

* [CIRC-2156](https://folio-org.atlassian.net/browse/CIRC-2156) Upgrade "holdings-storage" to 8.0

## 24.2.0 2024-03-21

* Update `feesfines` interface version to 19.0 (CIRC-1914)
* Logging in `org.folio.circulation.domain` package (CIRC-1813)
* Logging in circulation domain: D-L (CIRC-1909)
* Logging in circulation domain: M-U (CIRC-1910)
* Logging in circulation resources: O-T (CIRC-1896)
* Check-in/check-out for virtual item (CIRC-1907)
* Revert CIRC-1793 - remove support for token `loan.additionalInfo` in patron notices (CIRC-1942)
* Partial revert of CIRC-1527: bring back the correct delay for scheduled Aged To Lost Fee charging job (CIRC-1947)
* User primary address Country token fetches the Country code in the patron notices (CIRC-1931)
* Add support for Actual Cost Records properties lost in deserialization (CIRC-1769)
* Search-slips endpoint skeleton (CIRC-1932)
* Drools 7.74.1, xstream 1.4.20 (CIRC-1954)
* Fix Hold Shelf Expiration to respect Closed Library Dates (CIRC-1893)
* Add missing permission set for allowed service points endpoint (CIRC-1953)
* Upgrade to RMB 35.1.1, Vert.x 4.4.6, Log4j 2.20.0, mod-pubsub-client 2.11.2 (CIRC-1962)
* Fix test `OverduePeriodCalculatorServiceTest#getOpeningDayDurationTest`
* Request delivery staff slip: Requester country token information displayed wrong `stripes-components.countries` (CIRC-1955)
* Add field `loan.additionalInfo` to notice context (CIRC-1946)
* Asynchronously publish the ITEM_CHECKED_OUT event and ignore the result (CIRC-1950)
* Handle circulation rules update events (CIRC-1958)
* Skip account creation for reminders without a fee (CIRC-1970)
* Fix returning same error code for different error messages (CIRC-1961)
* Do not refresh circulation rules cache on GET and PUT `/circulation/rules` (CIRC-1977)
* Print `pendingLibraryCode` in DCB CheckIn Slips (CIRC-1981)
* The option to print picks slips doesn't activate (CIRC-1994)
* Reminders schedule around closed days (CIRC-1920)
* Use query-based endpoint for to fetch items by barcode (CIRC-1999)
* Pass tenant's timezone in the payload while creating circ log entries (CIRC-2006)
* Set `reminderFee` as loan action (CIRC-1984)
* Implementation for Search Slips API (CIRC-1933)
* Reschedule reminders on renewal (CIRC-1968)
* Unit tests for DCB changes (CIRC-1988)
* Optionally refuse renewal of items with reminders (CIRC-1923)
* Fix incorrect information in the Circulation log for the user barcode for In-house use (CIRC-1985)
* Returning DCB title in response if the item is a virtual item (CIRC-2029)
* Update reminder scheduler to handle printed notices (CIRC-1925)
* Combine items with related records during pagination process (CIRC-2018)
* Upgrade the Actions used by API-related GitHub Workflows (FOLIO-3944)
* Fix patron information in circulation log for check-ins that are not tied to a loan or request (CIRC-2045)
* Add new `displaySummarry` field to the Item schema (CIRC-2036)
* Do not send "Aged to lost" notice for closed loan (CIRC-1965)
* Reschedule reminder notices on recall (CIRC-2030)
* Reschedule reminder notices on due date change (CIRC-2005)
* Upgrade RMB to v35.2.0 (CIRC-2048)
* Support circulationItem for edge-patron requests for DCB Integration (CIRC-1966)

## 24.0.0 2023-10-11

* Fix NPE when user without barcode tries to check out an item that is already requested (CIRC-1550)
* Show both decimals for fee amount tokens in patron notices (CIRC-1580)
* Fix unexpected error message when attempting to renew loan with non-loanable item (CIRC-1592)
* Add required module permissions to fix aged to lost item renewal issue (CIRC-1738)
* Add effective location discovery display name token support (CIRC-1584)
* Only notify patron for recalls that change due date (CIRC-1747)
* Make a TLR recall keep the same item when edited (CIRC-1760)
* Add required module permissions to fix aged to lost item renewal issue (CIRC-1738)
* Fix charge action for automated fee/fine created with service point name in `createdAt` (CIRC-1730)
* Remove search index fields from request's extended representation (CIRC-1752)
* Save call number, shelving order and pickup location name in request (CIRC-1753)
* Make "Declare lost" fail when a fee/fine should be charged and fee/fine owner doesn't exist (CIRC-1758)
* Add `additionalInfo` token to the fee/fine notice context (CIRC-1767)
* Handle fee/fine balance changed events (CIRC-1765)
* Add support for `feeCharge.additionalInfo` token in patron notices (CIRC-1766)
* Fix periodic test failures on Jenkins (CIRC-1771)
* Add support for `requester.departments` token in staff slips generated in Check-in app (CIRC-1591)
* Logging: override, policy, reorder (CIRC-1772)
* Add support for `requester.departments` token in staff slips generated in Requests app (CIRC-1590)
* Add support for `currentDateTime` token in staff slips generated in Requests app (CIRC-1581)
* Add support for `currentDateTime` token in staff slips generated in Check-in app (CIRC-1582)
* Bundle multiple aged to lost fees/fines into one single notice (CIRC-1725)
* Update staff slips to have the primary service point for an effective location (CIRC-1785)
* Fix incorrect spelling of fulfillment preference for requests (CIRC-253)
* Logging: packages representations, subscribers, validation (CIRC-1779)
* Add support for `requestDate` token in pick slips (CIRC-1784)
* Fix NPE during item context creation (CIRC-1797)
* Increase request-storage interface version (CIRC-1800)
* Do not refresh loan during update to avoid issues with R/W split (CIRC-1788)
* Circulation rules decoupling and timer-based refresh enhancement (CIRC-1783)
* Update copyright year (FOLIO-1021)
* Ensure API-related GitHub Workflows are used (FOLIO-3678)
* Bundle multiple overdue fines into one single notice (CIRC-1726)
* Logging: packages storage inventory-loans (CIRC-1792)
* Fix potential NPE for items in transit report (CIRC-1704)
* Add API for adding loan info (CIRC-1823)
* Pickup notices not sent when another item is Awaiting pickup (CIRC-1832)
* Migrate to Java 17 (CIRC-1830)
* Handle flag `tlrHoldShouldFollowCirculationRules` in TLR-settings (CIRC-1819)
* Do not prohibit check-out when another item is recalled (CIRC-1831)
* Implement checkout lock feature (CIRC-1756)
* Add allowedServicePoints to request policy (CIRC-1824)
* New endpoint for allowed service points (CIRC-1825)
* Determine allowed service points for ILR (CIRC-1826)
* Use correct permissions for circulation rules reloading (CIRC-1860)
* Respect allowed service points in Request Policy (CIRC-1834)
* Determine allowed service points for TLR (CIRC-1827)
* Fix immediately expiring actual cost records (CIRC-1778)
* Add missing permissions (CIRC-1866)
* Add code to error "Service point is not a pickup location" (CIRC-1876)
* Return allowed service points for all enabled request types (CIRC-1781)
* Filter request types based on item statuses (CIRC-1875)
* Fix spelling in an error message (CIRC-1877)
* Add support for logging variables (CIRC-1709)
* Logging: circulation rules (CIRC-1809)
* Fix missing pickup locations when Page request is edited (CIRC-1885)
* Add missing permission for add-info endpoint to work (CIRC-1848)
* Add support for address tokens in patron notices (CIRC-1789)
* Consider TLR-settings for Hold when fetching allowed service points (CIRC-1883)
* Logging: drools (CIRC-1880)
* Logging: rules package (CIRC-1881)
* Add support for token `loan.additionalInfo` in patron notices (CIRC-1793)
* Allowed service points: fix move operation (CIRC-1890)
* Improve logging circulation resources: A-D (CIRC-1894)
* Improve logging circulation resources: E-N (CIRC-1895)
* Create process to bill reminder fees (CIRC-1527)
* Setup job to run for billing of reminder fees (CIRC-1528)
* Add fee/fine charge info into reminder fees (CIRC-1906)
* Fix NPE in when getting user's personal info (CIRC-1905)

## 23.5.0 2023-02-23

* Allow hold TLR for a title with no holding records (CIRC-1677)
* Display correct numberOfRenewalsAllowed and numberOfRenewalsRemaining values in fee/fine notices (CIRC-1571)
* Fix improper JSON key for calendar date API (CIRC-1737, CIRC-1743)

## 23.4.0 2023-02-17

* Fix error message absence when clicking "Anonymize all loans" button for closed loans with associated closed fees/fines (CIRC-1731)
* Respect circulation rules for TLRs during check-in (CIRC-1693)
* Cancel actual cost lost item fees when declaring item lost (CIRC-1711)
* Fix end session permission issue (CIRC-1727)
* Event LOAN_RELATED_FEE_FINE_CLOSED published during canceling actual cost record should close the loan (CIRC-1662)
* Add user group item permission to check-in endpoint (CIRC-1719)
* Support `instance-storage` `10.0` interface version (CIRC-1697)
* Cancel actual cost record on check-in and renewal (CIRC-1444)
* Add the field "requester.patronGroup" to the Requester Object in the Staff Slip context, for pick slips generated in the Requests app (CIRC-1578)
* Add the field "user.preferredFirstName" to the User Object in the Notice context (CIRC-1585)
* Fix missing user barcodes in circulation log search results (CIRC-1708)
* Update logging configuration (CIRC-1645)
* Add the field "requester.preferredFirstName" to the Requester Object in the Staff Slip context, for hold, request delivery and transit slips generated in the Check in app (CIRC-1587)
* Hold shelf expiration date should respect closed library dates (CIRC-1657, CIRC-1658)
* Update publish item checkout event code (CIRC-1702)
* Fix NPE for In transit report processing (CIRC-1675)
* Fix issue with searching the least recalled loans (CIRC-1696)
* Add indicator to checkout screen when checkout due date is truncated (CIRC-1653, CIRC-1654)
* Unable to recall items with status Aged to lost, Declared lost, Claimed returned when TLR is enabled (CIRC-1683)
* Allow TLR recalls for In transit, In process, On order items (CIRC-1684)
* Update versions of interfaces `request-storage` to `5.0` and `request-storage-bacth` to `2.0` (CIRC-1685)
* Add integration test with Drools (CIRC-1679)
* Fix cross-tenant policy id causing 500 errors (CIRC-1668)
* Downgrade Drools to v7.73 (CIRC-1676)
* Add additional properties to Actual Cost Record: effectiveLocation, effectiveLocationId, contributors (CIRC-1674)
* Upgrade dependencies (CIRC-1672)
* Add /admin/health endpoint (CIRC-1670)
* Fix issue with mergeOpenPeriods not able to merge 3 intervals that abut each other (CIRC-1671)
* Move JsonSchemaValidator from main to test (CIRC-1673)
* Update actual cost record status upon expiration (CIRC-1649)
* Send TLR awaiting pickup notice on check in (CIRC-1655)

## 23.3.0 2022-10-27

* Upgrade `calendar` interface to `5.0` (CIRC-1648)
* Fix incorrect calculation of due date when fixed schedule is used (CIRC-1625)

## 23.2.0 2022-10-19

* Do not close the loan when Actual Cost Record is created (CIRC-1624)
* Additional fields for Actual Cost Record (CIRC-1632)
* Upgrade to RMB 35.0.0 and Vertx 4.3.3 (CIRC-1628)
* Add dependency on actual-cost-record-storage interface (CIRC-1631)
* Add error code to request already closed error for PUT endpoint (CIRC-1553)
* Add missing permissions for deleting scheduled patron notices (CIRC-1615)
* Consider TLRs during renewal (CIRC-1611)
* Update actual cost record schema (CIRC-1607)
* Refuse renewal when title-level recall exists (CIRC-1610)
* Change item status on request deletion (CIRC-1569)
* Fix NPE in Items In Transit Report (CIRC-1588)
* Add itemLimit parameter to the item limit error (CIRC-1574)
* Add missing user barcode to requests in Circ Log (CIRC-1604)
* Send scheduled notices for TLR linked to item according to Patron Notice Policy (CIRC-1593)
* Aged to lost: Closing "Lost and paid" status - ACTUAL COST (CIRC-1566)
* Support instance-storage 9.0, holdings-storage 6.0, item-storage 10.0 (CIRC-1596)
* TLR - check policies for all items of the instance (CIRC-1576)
* Send immediate notices for TLR linked to item according to Patron Notice Policy (CIRC-1558)
* Add support for more loan date formats (CIRC-1577)
* Allow to create a recall TLR when available item exists but page requests are not allowed by the policy (CIRC-1575)
* Support users interface 14.2, 15.0, 16.0 (CIRC-1572)
* Add notes v3.0 interface support (CIRC-1564)
* Actual cost - close aged to lost loan as paid when no processing fee should be charged (CIRC-1432)
* Request record is not correctly stored in the log (CIRC-1565)
* Declared lost/aged to lost item: renewal (effect on lost item fees) - ACTUAL COST (CIRC-728)
* Update check-in logic for actual cost fee refund (CIRC-730)
* Fix NPE when creating TLR Recall for instance without an open loan (CIRC-1548)
* Re-enable actual cost functionality (CIRC-1555)

## 23.1.0 2022-06-28

* Create Actual Cost Record when item declared lost if Actual Cost is selected in Lost Item Policy (CIRC-714)
* Create Actual Cost Record when item is aged to lost if Actual Cost is selected in Lost Item Policy (CIRC-894)
* Add tests for aging overdue items to lost (CIRC-895)
* Disable actual cost record creation (CIRC-1554)
* Update loan schema according to actual json response (CIRC-915)
* Do not fail when nonexistent location ID is passed to GET /circulation/rules/request-policy (CIRC-1169)
* Pull inventory repository creation up (CIRC-1418)
* POST /circulation/requests/instances must create `true` TLR when feature is enabled (CIRC-1442)
* Fix checkout closing request for different item of the same instance (CIRC-1450)
* Add missing permissions for circulation rules (CIRC-1453)
* Use identity map to cache item storage representation (CIRC-1454)
* Do not allow Hold TLRs for instance with available items (CIRC-1455)
* Add missing permission to POST /circulation/requests/instances (CIRC-1456)
* Extract item repository dependencies (CIRC-1458)
* Remove redundant notes permission (CIRC-1461)
* Change due date when item is renewed (CIRC-1463)
* Fix patron notices not going out for recalled loans (CIRC-1464)
* Update `logContextItem` properly when building `NoticeLogContextItem` (CIRC-1465)
* Remove most usages of item representation within domain (CIRC-1466)
* Populate `effectiveLocationCampus` and `effectiveLocationInstitution` properly when sending TLR confirmation notice (CIRC-1467)
* Add missing circulation.override permission definitions (CIRC-1469)
* Fix two recall-related automated patron blocks that do not enforce (CIRC-1471)
* Publish LOAN_CLOSED event when last Lost Item Fee for loan is closed (CIRC-1474)
* TLR Recall should pick item with loan with next closest due date if another recall request exists, and the one with the least recalls if there are no not recalled loans (CIRC-1475)
* Extend due date of a loan after recall (CIRC-1476)
* Do not check request policy for TLR Holds and create Recalls first when placing instance level requests (CIRC-1479)
* Refuse ILR creation when TLR for the same instance already exists (CIRC-1481)
* Truncate loan due date during checkout only if there is a recall request on the same item (CIRC-1488)
* Use fixed date time during refund processing tests (CIRC-1494)
* Fix TLR notice circulation log record (CIRC-1495)
* Cancel aged to lost and declared lost fees that are paid/transferred in full when item is returned (CIRC-1496)
* Set due dates of loans with fixed due date loan policies using the correct loan period (CIRC-1497)
* Pick item closest to pickup service point for Page TLR (CIRC-1500)
* Log request id and tenant id (CIRC-1501)
* Update logic for publishing LOAN_DUE_DATE_CHANGED events (CIRC-1503)
* Do not send Request Expiration notices for `Closed - Filled` requests (CIRC-1504)
* Display correct previous date in circulation log when item is recalled (CIRC-1505)
* Fix instance level request endpoint, create ILRs when TLR feature is disabled (CIRC-1507)
* Replace deprecated logger (CIRC-1510)
* Delete overnight notice when failed to build template context for it (CIRC-1515)
* Fetch expired or cancelled requests in bathes when building Hold Shelf Clearance Report (CIRC-1517)
* Add policy IDs to the Account record (CIRC-1526)
* Update the failure message of the closed request validator (CIRC-1530)
* Allow to move TLR Recalls and Pages to another item, but not Holds (CIRC-1531)
* Don't pass dash comment with lost item fees (CIRC-1532)
* Format monetary values in patron notices to always show 2 decimal places (CIRC-1537)
* Upgrade Vert.x to 4.2.7, Spring to 5.2.22, mod-pubsub-client to 2.4.3 and xstream to 1.4.19 (CIRC-1539)
* Extend errors with enum values as parameters (CIRC-1541)
* Add manual block subpermissions for renew-by-barcode endpoint (CIRC-1545)
* Fulfill TLR Recall when item of the same instance is returned (CIRC-1549)

## 23.0.0 2022-02-22

* Stop using Joda Time (CIRC-966)
* Add missing permission to Check-in API (CIRC-1300)
* Add missing permission to fee/fine scheduled notices processing API (CIRC-1100)
* Implement request data migration in business logic (CIRC-1287)
* Implement request data migration in tests (CIRC-1288)
* Consider title-level requests during check-in (CIRC-1296)
* Tests fail if build server time zone is not UTC (CIRC-1327)
* Update dependencies to fix build failure: mod-pubsub to 2.4.0, RMB to 33.1.1, Vert.x to 4.2.1 (CIRC-1326)
* Recalls not being honored when renewal or due date change took place (CIRC-1112)
* Add description to renewed through override actions in Circ Log (CIRC-1344)
* Handle scheduled notices for TLRs properly (CIRC-1290)
* Handle immediate notices for TLRs properly (CIRC-1337)
* Consider title-level requests during check-out (CIRC-1297)
* Add holdingsRecordId to request record (CIRC-1353)
* Remove holdings JSON representation farom domain model (CIRC-1365)
* Add Publication and Editions to request JSON (CIRC-1364)
* Consider TLRs when changing loan due date (CIRC-1362)
* Fix broken tests (CIRC-1374)
* Improve error handling when a pubsub event can't be published (CIRC-1356)
* Remove holdingsRecordId from item in request (CIRC-1376)
* Remove holdings JSON representation from domain model (CIRC-1365)
* Add new Action when an automated fee/fine is charged if item is aged to lost (CIRC-1373)
* Remove instance JSON representation from domain model (CIRC-1366)
* Consider title-level requests during request queue management (CIRC-1298)
* Remove material type JSON representation from domain model (CIRC-1367)
* Consider TLRs when creating recall requests (CIRC-1360)
* TLR response does not contain instance data (CIRC-1375)
* Link item to hold TLR when upon check-in (CIRC-1359)
* Upgrade to Log4j 2.16.0 (CIRC-1387)
* Remove item.holdingsRecordId from Request JSON (CIRC-1385)
* Publication data is missing in Instance returned by Requests API (CIRC-1394)
* Extract permission set for fetching inventory records (CIRC-1382)
* Disable request queue validation for page requests always being at the top (CIRC-1397)
* Consider TLRs when validating new request against existing loans (CIRC-1361)
* Link page TLR to the item upon creation (CIRC-1358)
* Fix broken scheduled age-to-lost fee fine charging (CIRC-1386)
* On check-in page TLR linked to an item should be updated, not first TLR in the queue (CIRC-1403)
* Require request-storage 4.0 (CIRC-1407)
* Require request-storage-batch 1.0 (CIRC-1408)
* Missing item reference causing patron action session to not expire (CIRC-1286)
* Remove loan type JSON representation from domain model (CIRC-1368)
* TLR should be refused when instance/item is already requested (CIRC-1395)
* Extend request returned by Request Queue API with additional fields (CIRC-1402)
* Upgrade to Log4j 2.17.1 (CIRC-1415)
* Remove org.apache.httpcomponents:httpclient-* (CIRC-1414)
* Fix dueDate comparison in LoanDueDatesAfterRecallTests (CIRC-1417)
* Update copyright year (FOLIO-1021)
* Use new api-doc (FOLIO-3231)
* Fix incorrect error message when billing aged to lost items (CIRC-1177)
* Requests should change position when they go in fulfilment on check-in (CIRC-1412)
* Fix creation of TLR for instances with 10+ items (CIRC-1413)
* Broken scheduled notices are blocking the queue (CIRC-1357)
* Withdrawn item status causes scheduled notices to remain queued (CIRC-1406)
* Improve handling of scheduled-age-to-lost-fee-charging processing for broken loans (CIRC-1404)
* Add missing module permissions to Requests API (CIRC-1410)
* Request not allowed issue after TLR feature merge (CIRC-1422)
* Request queue breaks when request is moved to another item (CIRC-1424)
* During check-in only update fulfillable request if it's for the same item (CIRC-1421)
* Handle non-critical errors during check-out (CIRC-1046)
* Add missing permissions for Check-out API (CIRC-1433)
* Scheduled Notices are sent after the loan is marked as Claimed Returned (CIRC-1427)
* Circulation rules saved if criteria entered without ':' and without policies (CIRC-1331)
* Fix incorrect item status management when request is moved (CIRC-1436)
* Refuse to move request to an item from different instance (CIRC-1430)
* Fix validations during check-out when TLR feature is enabled (CIRC-1435)
* Fix request creation without request date (CIRC-1371)
* In-transit report performance improvements (CIRC-1338, CIRC-1339, CIRC-1340, CIRC-1341, CIRC-1342, CIRC-1343, CIRC-1347, CIRC-1420)

## 22.1.0 2021-10-08

* Screen is hanging on a loading screen when trying to view loan details on the Circulation log page (CIRC-1188)
* Circ log filter for renewed doesn't work (CIRC-1165)
* Increases the number of loans to be checked for scheduled anonymization to 50 000 (CIRC-1178)
* LOAN_CLOSED event will be published when loan is closed as a result of being declared lost (CIRC-1197)
* Errors related to immediate patron notices will be logged to circulation log (CIRC-1183)
* Manual patron block for requests will not block renewals and borrowing (CIRC-1185)
* Overdue fine will not be charged for exceptional closed periods (CIRC-1184)
* Errors related to scheduled patron notice processing will be logged to circulation log (CIRC-1180)
* Adds tests for renewal due date truncation (CIRC-888)
* Does not allow patrons that are `expired` or `inactive` to renew items (CIRC-1187)
* Increases the number of loans to be checked for scheduled anonymization to 50 000 (CIRC-1178)
* Upgrades to vert.x 4.x (CIRC-1053)
* Requires `item-storage 8.7 or 9.0`
* Requires `holdings-storage 1.3, 2.0, 3.0, 4.0 or 5.0`
* Requires `instance-storage 4.0, 5.0, 6.0, 7.0 or 8.0`
* Requires `feesfines 16.3 or 17.0`

## 22.0.0 2021-06-14

* Due date will be truncated by patron expiration (CIRC-886, CIRC-1159)
* Will charge overdue fines at check in when claimed returned (CIRC-997)
* Renewals can be overridden using the same API as non-overridden requests (CIRC-1143)
* Remove redundant override-check-out-by-barcode endpoint (CIRC-1064)
* Remove redundant override-renewal-by-barcode endpoint (CIRC-1091)
* Manual blocks for renewals can be overridden (CIRC-1092)
* Aged to lost process now charges fees for actual cost items if a processing fee is set (CIRC-1115)
* Overdue fines not charged if item due before library closes but returned after library has been closed (CIRC-1120)
* Items cannot be requested by blocked patron even when the block does not expire (CIRC-1078)
* Pickup notice is sent when higher priority request is cancelled or expires (CIRC-1141)
* Update version of mod-pubsub-client to fix memory leak (CIRC-1121)
* Use `_timer` interface to periodically execute age to lost background processes (CIRC-1144)
* Add notice post perm to change due date (CIRC-1114)
* Adapt old checkout override tests to cover new overriding functionality (CIRC-1106)
* Checks manual blocks during checkout (CIRC-1072)
* Errors when filtering on recall requested (CIRC-1126)
* Source of circulation actions is displaying differently in the circulation log than in the item record (CIRC-1125)
* Request expiration should be 23:59 of day selected (CIRC-1119)
* Not receiving change due date notice (CIRC-1114)
  * Circulation log will contain entry when due date is changed (CIRC-1140)
* Fixes hold shelf expiration request notice (CIRC-1129)
* Overdue fines not charged if item due before library closes but returned after library has been closed (CIRC-1120)
* Added lost-items-fees-policies.collection.get permission to the checkin-by-barcode endpoint (CIRC-1117)
* Some filters not finding recent results (CIRC-1133)
* Fix memory leak caused by mod-pubsub-client (CIRC-1121)
* Request pick up notice not sent when requester's barcode has been changed after request was created (CIRC-1139)
* Provides `circulation 11.0`
* Provides `declare-item-lost 0.3`

## 20.1.0 2021-03-30

* Refund/cancel Aged to lost fees/fines when declaring an item lost (CIRC-1077)
* Lost permissions for /circulation/check-in-by-barcode (CIRC-1099)
* Provides `declare-item-lost 0.3` (CIRC-1077)

## 20.0.0 2021-03-12

* No longer periodically executes age to lost background processes (CIRC-1084)
* Does not charge overdue fees when fee refund period has passed (CIRC-1000)
* Loan dates are truncated when there is a recall request in the queue (CIRC-1018)
* Schedules patron notices when items are aged to lost (CIRC-962)
* Schedules patron notices when fees or fines are charged or adjusted (CIRC-963, CIRC-964)
* Introduces patron comments on requests (CIRC-988, CIRC-1036, CIRC-1037)
* Sends recalled patron notices to borrower even when due date does not change (CIRC-989)
* Policy determines whether loans may be extended when item is recalled (CIRC-994)
* Applies separate policy for recalled items that are aged to lost (CIRC-1005)
* `Intellectual` items may not be checked out or checked in (CIRC-1008, CIRC-1011)
* `Restricted` items may be requested (CIRC-1085)
* Blocks may be overridden during check out, renewal and requesting (CIRC-1061, CIRC-1062, CIRC-1063)
* Provides `requests-reports 0.8`
* Provides `pick-slips 0.3`
* Provides `circulation 9.5`
* Provides `age-to-lost-background-processes 0.1`
* Requires `item-storage 8.7`
* Requires `request-storage 3.4`
* Requires `feesfines 16.3`
* Requires `notes 1.0 or 2.0`

## 19.2.0 2020-10-15

* Publishes audit log events for loans (CIRC-933)
* Publishes audit log events for requests (CIRC-930)

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
