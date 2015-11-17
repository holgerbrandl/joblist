#!/usr/bin/env bash

mkdir test
cd test

bsub "touch foo.txt" 2>/dev/null

bsub "touch foo.txt" | jl add .jobs
jl wait

## shortcut installation

eval "$(jl shortcuts)"


cd $(dirname $(which jl))/..
sbt assembly; bsub "touch foo.txt" | jl add .myjobs && jl wait
