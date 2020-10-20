#!/usr/bin/env bash

#remove log output
rm tests.log

mvnTargets="clean org.jacoco:jacoco-maven-plugin:prepare-agent test"
teeCommand="tee -a tests.log"

if [ -z "$1" ]
  then
    mvn $mvnTargets | $teeCommand
  else
    echo "Using test argument: $1"
    mvn -Dtest=$1 $mvnTargets | $teeCommand
fi


