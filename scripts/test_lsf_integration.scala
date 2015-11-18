

import joblist._

val jl = JobList

jl.run(JobConfiguration("echo test"))
jl.run(JobConfiguration("echo test"))

// block execution until are jobs are done
jl.waitUntilDone()

// get jobs that hit the Wall limit and resubmit them with more cores
val failedConfigs: Iterable[JobConfiguration] = jl.jobConfigs.filterKeys(!jl.jobIds.contains(_)).values

failedConfigs.map(_.copy(numThreads = 10)).foreach(jl.run)
