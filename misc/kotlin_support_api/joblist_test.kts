#!/usr/bin/env kscript

//DEPS de.mpicbg.scicomp.joblist:joblist-kotlin:1.1


import de.mpicbg.scicomp.kutils.joblist.JobConfig
import de.mpicbg.scicomp.kutils.joblist.JobList


val jl = JobList()

jl.run(JobConfig("sleep 10"))
jl.run(JobConfig("sleep 23; exit 1", name="failing job"))

jl.run(JobConfig("echo 'hello'", numThreads = 23))

jl.waitUntilDone(1000)
