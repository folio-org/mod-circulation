# mod-circulation
Copyright (C) 2017 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

# Goal

FOLIO compatible circulation capabilities, including loan items from the inventory.

# Prerequisites

# Required

- Java 8 JDK
- Gradle 3.3

## Optional

- Node.js 6.4 (for API linting)
- NPM 3.10 (for API linting)

# Preparation

## Git Submodules

There are some common RAML definitions that are shared between FOLIO projects via Git submodules.

To initialise these please run `git submodule init && git submodule update` in the root directory.

If these are not initialised, the inventory-storage module will fail to build correctly, and other operations may also fail.

More information is available on the [developer site](http://dev.folio.org/doc/setup#update-git-submodules).

# Common activities

## Running a general build

In order to run a general build (including the default tests), run `gradle build`.

## Creating the circulation module JAR

In order to build an executable Jar (e.g. for Okapi to deploy), run `gradle fatJar`.

## Checking the RAML and JSON.Schema definitions

run `./lint.sh` to validate the RAML and JSON.Schema descriptions of the API (requires node.js and NPM)
