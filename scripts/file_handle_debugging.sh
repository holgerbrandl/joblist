#!/usr/bin/env bash


## too many open files
## known problem: https://wiki.jenkins-ci.org/display/JENKINS/I'm+getting+too+many+open+files+error
## http://stackoverflow.com/questions/4289447/java-too-many-open-files
lsof -p 43158
lsof -p 7849


lsof -p 43158 | wc -l
lsof -p 43158 | sort | uniq