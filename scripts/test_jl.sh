#!/usr/bin/env bash

mkdir test
cd test

bsub "touch foo.txt" 2>/dev/null

bsub "touch foo.txt" | jl add .jobs

## shortcut installation

eval "$(jl shortcuts)"

