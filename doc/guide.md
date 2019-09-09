# Guide

This is a guide meant to help you both use the `mod-circulation` module and
enable you to contribute to the module.

## Automated Testing

See [companion document](automated-testing.md) for information on the automated testing approach 
used in mod-circulation. 

## Storage

This circulation module has no persistent storage of its own. Therefore, it
should be used in combination with another module that offers such a storage,
for example `mod-circulation-storage`. For the current version, this
`mod-circulation-storage` offers an interface (API) for storing:
* fixed due date schedules
* loan policies
* loan rules (as a single entity/entry)
* loans
* requests

## How does the module work?

This module actually handles the loans and the requests for those loans.

### Requests and loans

A `request` for an `item` can be created by a requesting user. Both item id and
requesting user id are stored in the newly created `request`. If the `item` is picked up,
the `request` is closed and a `loan` is created. The corresponding `loan` consists
of the same item id and requester id.

### What about the items and requesting users?

Well, the items and requesting users don't live in this module themselves, but can
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

See list of item and request statuses in the [README](../README.md) file

So, if a request for an item that is already checked out to another user
is created, the request status becomes "not yet filled". If the item is checked
in AND the request is the first in the list of requests [^1], the request status
is set to "awaiting pickup". If the item is then picked up, the request is
closed and a loan is created.

[^1]: Currently this is ordered by creation date (time stamp) and can not be
altered.

