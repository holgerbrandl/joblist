
import joblist._


val jl = JobList()

jl.run(JobConfiguration("echo foo"))
jl.run(JobConfiguration("echo bar"))

// block execution until are jobs are done
jl.waitUntilDone()

// get the run information about jobs that were killed by the queuing system
val killedInfo: List[RunInfo] = jl.jobs.
  filter(job => jl.killed.contains(job.id)).
  map(_.info)


// resubmit them with more threads
//failedConfigs.map(_.copy(numThreads = 10)).foreach(jl.run)
jl.resubmitKilled(new BetterQueue("long"))
