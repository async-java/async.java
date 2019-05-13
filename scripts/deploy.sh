#!/usr/bin/env bash

# https://medium.freecodecamp.org/how-to-upload-an-open-source-java-library-to-maven-central-cac7ce2f57c

# https://dzone.com/articles/publish-your-artifacts-to-maven-central

gpg --export -a 437A774CD1F35D00 | pbcopy  # public_key

gpg --export -a 437a774cd1f35d00 | pbcopy

export GPG_TTY=$(tty)
mvn deploy  -Dmaven.test.skip=true #  -Dmaven.javadoc.skip=true +++ use "mvn clean deploy"