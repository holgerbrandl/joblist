#!/usr/bin/env bash



for num in $(seq 1 2); do
    echo "processing $num"
#    remdebug_jl submit --debug "sleep 10"
    jl submit --debug "echo hallo; sleep 10"
done

jl status