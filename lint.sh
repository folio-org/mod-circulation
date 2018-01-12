#!/usr/bin/env bash

yarn install
./node_modules/.bin/raml-cop ramls/circulation.raml
