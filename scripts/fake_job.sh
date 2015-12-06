#!/usr/bin/env bash


if [ $# -ne 2 ]; then
    echo "Usage: `basename $0` <sleep_time> <fail_prob>"; exit;
fi

sleep $1

#http://stackoverflow.com/questions/2493642/how-does-a-linux-unix-bash-script-know-its-own-pid
echo "my pid is $$"

# http://stackoverflow.com/questions/2556190/random-number-from-a-range-in-a-bash-script
randFail=$(perl -e 'print int(rand(100))')
echo "randfail is $randFail"

if [ "$2" -lt "$randFail" ]; then
    echo "job failed" 1>&2
    exit 1
else
    echo "job succeeded"
    exit 0
fi

