#!/usr/bin/env bash

mkdir test
cd test

bsub "touch foo.txt" 2>/dev/null

bsub "touch foo.txt" | jl add .jobs
jl wait

## shortcut installation
#eval "$(jl shortcuts)"

(cd $(dirname $(which jl))/.. && sbt assembly)

cd ~/unit_tests
bsub "touch foo.txt" | jl add .myjobs
jl wait .myjobs

#jlsub .myjobs


## fake a walltime hit


# https://bmi.cchmc.org/resources/software/lsf-examples

mcdir ~/jl_test

jl submit --other_queue_args "-W 00:01" "sleep 12"


## failing example which requires resubmission

#bsub -W 00:01 "sleep 120; touch whit.txt" |  jl add .whit
#jl wait

jl submit -j .whit "echo foo"
jl submit -j .whit "echo bar"
jl submit -j .whit -O "-W 00:01" "sleep 80; touch whit.txt"
jl wait --resubmit_wall "00:10" .whit

jl stats --report


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



################################
## single quote support
################################

## see https://github.com/holgerbrandl/joblist/issues/11


## local works anyway

## works with LSF
bsub <<"JLCMD"
JLCMD

echo "touch tttt" | bsub


## slurm
mcdir test_jl

echo '#!/bin/bash
touch hello_slurm.txt' | sbatch $submitArgs -e err.log -o out.log
rm hello_slurm.txt

sbatch $submitArgs -e err.log -o out.log <<"EOF"
#!/bin/bash
touch hello_slurm.txt
EOF



mcdir ~/jl_test
jl reset
jl submit --wait "echo '10'"
jl status
jl status --log out
jl status --log err
jl status --log cmd
more .jl/* | cat


## enhanced reporting

jl reset
jl submit "sleep 20; echo hallo; exit 1 "
jl submit "sleep 25; echo foo ; echo 'bar' >&2;  exit 0"
jl submit "sleep 10; echo baumaus"
jl wait
jl status --report

