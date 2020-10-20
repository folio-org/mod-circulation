#!/usr/bin/env bash

#remove log output
rm tests.log

if [ -z "$1" ]
  then
    mvn clean org.jacoco:jacoco-maven-plugin:prepare-agent test | tee -a tests.log
  else
    echo "Using test argument: $1"
    mvn -Dtest=$1 clean org.jacoco:jacoco-maven-plugin:prepare-agent test | tee -a tests.log
fi


