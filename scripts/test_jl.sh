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

#jl --help
jl reset
jl submit -j .whit "echo foo"
jl submit -j .whit "echo bar"
## direct lsf walltime
#jl submit -j .whit -O "-W 00:01" "sleep 80; touch whit.txt"
## newjl walltime
jl submit -j .whit -w 00:01 "sleep 80; touch whit.txt"
jl wait
jl resub --time "00:10" .whit

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
## SLURM job submssion methods
################################

## local works anyway

## works with LSF
bsub <<"JLCMD"
JLCMD

echo "touch tttt" | bsub


## slurm
mcdir test_jl

echo '#!/bin/bash
echo some out
echo some err >&2
touch some_file.txt
' > test.sh
sbatch  -e err.log -o out.log test.sh

echo '#!/bin/bash
touch hello_slurm.txt
' | sbatch  -e err.log -o out.log
rm hello_slurm.txt


sbatch $submitArgs -e err.log -o out.log <<"EOF"
#!/bin/bash
touch hello_slurm2.txt
EOF


sbatch --wrap='
touch hello_slurm.txt
touch hello_slurm_3.txt
'

#http://stackoverflow.com/questions/29810186/is-there-a-one-liner-for-submiting-many-jobs-to-slurm-similar-to-lsf

## forced oneliner
echo -e '#!/bin/bash'"\n"'touch hello_slurm.txt' | sbatch -e err.log -o out.log

## http://stackoverflow.com/questions/2128949/how-to-pipe-a-here-document-through-a-command-and-capture-the-result-into-a-vari
## http://stackoverflow.com/questions/7046381/syntax-for-piping-a-heredoc-is-this-portable

################################
## single quote support
################################

## see https://github.com/holgerbrandl/joblist/issues/11




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

