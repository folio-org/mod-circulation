#!/usr/bin/env bash

#remove log output
rm tests.log

mvnTargets="clean test surefire-report:report jacoco:report site"
teeCommand="tee -a tests.log"

if [ -z "$1" ]
  then
    mvn $mvnTargets | $teeCommand
  else
    echo "Using test argument: $1"
    mvn -Dtest=$1 $mvnTargets | $teeCommand
fi
