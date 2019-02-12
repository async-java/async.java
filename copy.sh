#!/usr/bin/env bash


source=`dirname "$0"`
dest="$HOME/codes/oresoftware/reloader.java"

rsync --exclude=.git --exclude=.idea -r "$source/" "$dest/"