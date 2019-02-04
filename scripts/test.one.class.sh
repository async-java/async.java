#!/usr/bin/env bash

#mvn test -Dtest=AsyncTest#testSeries

#mvn clean test -Dtest=AsyncTest#testInjectSimple

mvn clean test -Dtest=general.AsyncTest#testQueue

