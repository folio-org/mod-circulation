#!/usr/bin/env bash

#remove log output
rm tests.log

mvn clean org.jacoco:jacoco-maven-plugin:prepare-agent test | tee -a tests.log
