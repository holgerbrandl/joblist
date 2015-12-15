
import joblist._


val jl = new JobList("/home/h3/brandl/assembly_blast/dd_Dben_v3.29409/.blastx")
//jl.reset()

jl.jobs.size
jl.jobs.filterNot(_.isFinal).map(_.id).contains()

val missingInfoJob = jl.jobs.filterNot(_.isFinal).find(_.id == 2134570).get
missingInfoJob.infoFile.isRegularFile
missingInfoJob.updateStatsFile()



// block execution until are jobs are done
jl.waitUntilDone()

// get the run information about jobs that were killed by the queuing system
val killedInfo: List[RunInfo] = jl.killed.map(_.info)


// resubmit to other queue
jl.resubmit()

//failedConfigs.map(_.copy(numThreads = 10)).foreach(jl.run)
