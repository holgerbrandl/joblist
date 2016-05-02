#!/bin/sh
exec scala -J-Xmx4g -cp "$(ls $(dirname $0)/../../target/scala-2.11/joblist-assembly*jar)" -savecompiled "$0" "$@"
!#


import joblist._


val jl = new JobList(".auto_quit")
jl.reset()

jl.run(JobConfiguration("sleep 3"))
jl.run(JobConfiguration("touch script_result.txt"))

jl.waitUntilDone()

println("should be done now and exit")
