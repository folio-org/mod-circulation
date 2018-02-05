#!/usr/bin/env bash

mvn -q clean org.jacoco:jacoco-maven-plugin:prepare-agent test
