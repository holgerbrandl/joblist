

// tbd convert into test

import joblist._


val jl = JobList()
jl.reset()

jl.run(JobConfiguration("ll does_not_exist"))
jl.run(JobConfiguration("echo bar"))
jl.run(JobConfiguration("echo 'this failed >&2; exit 1"))
jl.printStatus()

// block execution until are jobs are done
jl.waitUntilDone()

JobListCLI.main("status --failed".split(" "))
JobListCLI.main(Array("status"))

new java.io.File(".").getCanonicalPath()