#!/usr/bin/env bash

## http://stackoverflow.com/questions/2556190/random-number-from-a-range-in-a-bash-script
## http://stackoverflow.com/questions/18612603/redirecting-output-of-bash-for-loop
cd jl_test
for job_nr in $(seq 1 10); do
    echo "sleep $(perl -e 'print int(rand(20)) + 10'); echo slept for some time in $job_nr"
done | jl submit --batch -