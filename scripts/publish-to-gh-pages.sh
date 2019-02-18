#!/usr/bin/env bash

#mvn clean javadoc:javadoc
#rsync -r "$PWD/target/site/apidocs/" ../async-java.github.io/
git subtree push --prefix target/site/apidocs gh-pages master