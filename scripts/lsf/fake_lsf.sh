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

# todo mimic bjobs better
# echo the
#cat .joblist
#sleep 3


echo ""
}