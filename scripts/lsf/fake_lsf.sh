#!/usr/bin/env bash



##
## A fake set of lsf-utiled used by jl
##

bsub(){
if [ $# -eq 0 ]; then echo "Usage: bsub [fake args]* <eval_command>+"; return; fi

sleep 1

## run last argument which is supposed to be the cmd
## inspired by http://stackoverflow.com/questions/1853946/getting-the-last-argument-passed-to-a-shell-script
for last; do true; done
#echo $last

eval $last

echo "Job <$RANDOM> was submitted" 1>&2
}

bjobs()
{

echo
"JOBID   USER    STAT  QUEUE      FROM_HOST   EXEC_HOST   JOB_NAME   SUBMIT_TIME
JOBID   USER    STAT  QUEUE      FROM_HOST   EXEC_HOST   JOB_NAME   SUBMIT_TIME  PROJ_NAME CPU_USED MEM SWAP PIDS START_TIME FINISH_TIME
744191  brandl  DONE  short      falcon      n01         echo test; sleep 1 11/17-18:10:50 default    000:00:00.01 2648   33112   -  11/17-18:10:56 11/17-18:10:57"


# todo mimic bjobs better
# echo the
#cat .joblist
#sleep 3
}