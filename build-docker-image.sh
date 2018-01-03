#!/usr/bin/env bash

mvn clean package -q -Dmaven.test.skip=true || exit 1

docker build -t mod-circulation .

