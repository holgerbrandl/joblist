
import joblist._


val jl = JobList()

jl.run(JobConfiguration("echo foo", queue = "haswell"))
jl.status
jl.run(JobConfiguration("echo bar"))

// block execution until are jobs are done
jl.waitUntilDone()

// get the run information about jobs that were killed by the queuing system
val killedInfo: List[RunInfo] = jl.killed.map(_.info)


// resubmit to other queue
jl.resubmitFailed(new BetterQueue("long"))

//failedConfigs.map(_.copy(numThreads = 10)).foreach(jl.run)
