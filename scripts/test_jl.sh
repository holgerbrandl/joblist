#!/usr/bin/env bash

mkdir test
cd test

bsub "touch foo.txt" 2>/dev/null

bsub "touch foo.txt" | jl add .jobs
jl wait

## shortcut installation

eval "$(jl shortcuts)"


(cd $(dirname $(which jl))/.. && sbt assembly)

cd ~/unit_tests
bsub "touch foo.txt" | jl add .myjobs
jl wait .myjobs

eval "$(jl shortcuts)"
wait4jobs .myjobs


## fake a walltime hit


# https://bmi.cchmc.org/resources/software/lsf-examples

mcdir ~/jl_test

eval "$(jl shortcuts)"

jsub --other_queue_args "-W 00:01" "sleep 12"


## failing example which requires resubmission

jsub --other_queue_args "-W 00:01" "sleep 12" |  jl add

wait4jobs --resubmit_wall "00:10"

jl stats
