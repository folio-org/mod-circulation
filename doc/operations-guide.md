# Operations Guide

## Scheduled Age to Lost Processes

The age to lost processes are made up of two processes:
* age to lost (the /circulation/scheduled-age-to-lost endpoint)
* aged to lost fee charging (the /circulation/scheduled-age-to-lost-fee-charging endpoint)

By default, these are scheduled to execute every 30 or 35 minutes respectively. This configuration can be changed via Okapi, by using the [timer management API](https://github.com/folio-org/okapi/blob/master/doc/guide.md#timer-management).

Each execution of these processes can only process a maximum of 1 000 000 loans.
