# Guide

## Storage

This circulation module has no persistent storage of its own. Therefore, it 
should be used in combination with another module that offers such a storage, 
for example `mod-circulation-storage`. For the current version this 
`mod-circulation-storage` offers an interface (API) for storing: 
* fixed due date schedules
* loan policies
* loan rules (as a single entity/entry)
* loans
* requests

Please consult the `doc/guide.md` within `mod-circulation-storage` for an 
up-to-date guide on how to use the API

## How does the module work?

This module actually handles the loans and the requests for those loans. 

### Requests and loans

A request for an item can be created by a requester. Both item id and 
requester id are stored in the newly created request. If the item is picked up, 
the request is closed and a loan is created. The corresponding loan consists 
of the same item id and requester id.

### What about the items and requesters?

Well, the items and requesters don't live in this module themselves, but can 
be retrieved by their id from other modules, responsible for users and 
inventory.

### Items? Not instances?

The terminology might be a little confusing, but the `item` is the (mostly)
physical copy of a resource (which has a location), whereas the `instance` 
refers to an instance of a `work`. *(see `BIBFRAME 2.0` for more information)*

Currently, only a specific item can be requested. So, if a library usually has 
three paperback copies of "George Orwell - 1984" on the shelf but none at the
moment, is not possible to request "the first that comes available again".

### Statuses

#### Item statuses

Items can have the following statuses:
* "Available"
* "Checked out"
* "Checked out - Held"
* "Checked out - Recalled"

#### Request statuses

Requests can have the following statuses:
* "Open - Not yet filled"
* "Open - Awaiting pickup"
* "Closed - Filled" 

So, if a request for an item that is already checked out to another requester
is created, the request status becomes "not yet filled". If the item is checked
in AND the request is the first in the list of requests(*), the request status 
is set to "awaiting pickup". If the item is then picked up, the request is 
closed and a loan is created.

*) Currently this is ordered by creation date (time stamp) and can not be 
altered.  

