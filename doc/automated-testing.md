# Automated Testing

## API Tests

### Fake Modules

The API tests use a fake implementation of interfaces that are used to fetch 
or update records

These fakes are configured in the FakeOkapi class.

#### Schema Validation

In order to try to identify potentially mismatches between the fake modules and 
the reference implementations, it is possible to configure them to use a copy of
an interface JSON schema to validate incoming representations during creation or 
update of records.

##### Location of Schemas

Copies of the relevant storage schema are kept in the test resources directory.

They are kept in the root in order to make it easier for schema references to be
resolved, as the shared RAML definitions are included in the main resources

They are named after the record and interface version, for example, the loan 
record from the `loan-storage 6.1` interface is named `storage-loan-6-1`.

##### Updating a Schema

When an interface dependency is updated, any schema that are used within the 
tests should also be updated, other the tests might produce false results.

As the names include the interface version, the file should also be renamed. 
And the paths in the `StorageSchema` class need to be changed.

##### Test standards

* assertThat import used is org.hamcrest.MatcherAssert.assertThat

###### The reason behind why org.hamcrest.MatcherAssert.assertThat was chosen:

* org.junit.Assert.assertThat() is deprecated as of JUnit 4.13.
* custom hamcrest matchers may include more detailed information about what exactly was wrong
* control version of Hamcrest
