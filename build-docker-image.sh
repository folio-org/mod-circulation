#!/usr/bin/env bash

gradle fatJar

docker build -t mod-circulation .

