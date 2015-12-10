
import joblist._


val jl = JobList()
jl.reset()

jl.run(JobConfiguration("echo foo"))
jl.run(JobConfiguration("echo bar"))
jl.printStatus()

// block execution until are jobs are done
jl.waitUntilDone()

// get the run information about jobs that were killed by the queuing system
val killedInfo: List[RunInfo] = jl.killed.map(_.info)


// resubmit to other queue
jl.resubmit()

//failedConfigs.map(_.copy(numThreads = 10)).foreach(jl.run)
