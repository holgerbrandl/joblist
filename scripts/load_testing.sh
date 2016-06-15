#!/usr/bin/env bash

## http://stackoverflow.com/questions/2556190/random-number-from-a-range-in-a-bash-script
## http://stackoverflow.com/questions/18612603/redirecting-output-of-bash-for-loop

cd ~/unit_tests

ll /tmp/jl_devel/scripts
export PATH=/tmp/jl_devel/scripts:${PATH}
which jl

#http://unix.stackexchange.com/questions/12068/how-to-measure-time-of-program-execution-and-store-that-inside-a-variable
jl reset

START=$(date +%s)

for job_nr in $(seq 1 1000); do
    echo "sleep $(perl -e 'print int(rand(20)) + 10'); echo slept for some time in $job_nr"
done | jl submit --batch -

END=$(date +%s)
echo $(echo "$END - $START" | bc)


## todo convert this into a unit test
jl reset
jl submit -n test "echo foo"
jl submit -n test "echo bar"

jl wait