#!/usr/bin/env bash


gpg --export -a 437A774CD1F35D00 | pbcopy  # public_key

gpg --export -a 437a774cd1f35d00 | pbcopy

export GPG_TTY=$(tty)
mvn deploy  -Dmaven.test.skip=true #  -Dmaven.javadoc.skip=true +++ use "mvn clean deploy"