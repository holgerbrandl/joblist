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

#bsub -W 00:01 "sleep 120; touch whit.txt" |  jl add .whit
#jl wait

jl submit -j .whit "echo foo"
jl submit -j .whit "echo bar"
jl submit -j .whit -O "-W 00:01" "sleep 120; touch whit.txt"
jl wait --resubmit_wall "00:10" .whit

jl stats


## test qeueu status monitoring

cd test
remdebug_jl submit --jl .qs_test --wait "sleep 30"

for jobNr in $(seq 1 10); do
#    jl submit --jl .qs_test  "sleep 30"
#    drip_jl submit --jl .qs_test  "sleep 30"
#    sem -j 20 'jl submit --jl .qs_test  "sleep 30"' # will fail because timestamping does not work anymore
    jl submit --jl .qs_test  "touch $jobNr.txt; sleep 30"
done
wait
sem --wait

jl wait .qs_test
remdebug_jl wait .qs_test

rm -rf .jl .qs_test .logs
killByName Dropbox

## batch mode

rm cmds.txt
for jobNr in $(seq 1 10); do
    echo  "touch $jobNr.txt; sleep 30" >> cmds.txt
done
cat cmds.txt

jl submit --jl .qs_test -n batch_test --batch cmds.txt
jl wait --report .qs_test
jl submit --batch "echo test"
