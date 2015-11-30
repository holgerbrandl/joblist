package joblist

import java.time.Instant

import better.files.File
import joblist.lsf.LsfScheduler

import scalautils.Bash
import scalautils.CollectionUtils.StrictSetOps

/**
  * A stateless wrapper around list of cluster jobs. The actual data is stoed in a plain text-file containing the job-IDs (one per line).
  *
  * By design JobList are invalid until a job is added to them (see <code>.requireListFile</code>)
  *
  * @author Holger Brandl
  */

case class JobList(file: File = File(".joblist"), scheduler: JobScheduler = guessQueue()) extends AnyRef {

  def this(name: String) = this(File(name))


  implicit val JobList = this


  val resubGraphFile = File(file.fullPath + ".resubgraph")

  //
  // Model
  //

  /** Loads the current state of this joblist from disk. If there is no list files yet, it returns an empty list. */
  // Note: use cached .jobs instead
  private def ids = if (file.isRegularFile) file.allLines.map(_.toInt) else List()


  def jobs = {
    //    ids.map(Job(_))

    if (wasUpdated()) {
      _jobCache = ids.map(Job(_))
    }

    _jobCache
  }


  private var lastUpdate: Instant = null
  private var _jobCache: List[Job] = List()


  private def wasUpdated() = {
    if (file.isRegularFile && (lastUpdate == null || file.lastModifiedTime.isAfter(lastUpdate))) {
      lastUpdate = file.lastModifiedTime
      true
    } else {
      false
    }
  }


  //
  // Monitoring and stats
  //


  def killed = {
    jobs.filter(_.wasKilled)
  }


  def failed = {
    /* see man bjobs for exit code: The job has terminated with a non-zero status – it may have been aborted due
     to an error in its execution, or killed by its owner or the LSF administrator */
    jobs.filter(_.hasFailed)
  }


  def queueStatus = {
    //noinspection ConvertibleToMethodValue
    val jobIds = jobs.map(_.id) // inlining ot would cause it to be evaulated jl.size times
    scheduler.getQueued.filter(qs => jobIds.contains(qs.jobId))
  }


  def inQueue = queueStatus.map(qs => Job(qs.jobId))


  var lastQueueStatus: List[QueueStatus] = List()


  def isRunning: Boolean = {
    val nowInQueue = queueStatus

    // calculate a list of jobs for which the queue status changed. This basically an xor-set operation
    // (for which there's not library method)
    // this will detect changed status as well as presence/absence

    // note this type of xor is tested in joblist/TestTasks.scala:190
    val changedQS: Seq[Job] = nowInQueue.strictXor(lastQueueStatus).map(_.jobId).distinct.map(Job(_))

    // update runinfo for all jobs for which the queuing status has changed
    changedQS.foreach(updateStatsFile)

    println(s"${file.name}: In queue are ${nowInQueue.size} jobs out of ${jobs.size}")

    // update last status to prepare for next iteration
    lastQueueStatus = nowInQueue

    nowInQueue.nonEmpty
  }


  def waitUntilDone(msg: String = "", sleepInterval: Long = 10000) = {
    //// because sometimes it takes a few seconds until jobs show up in bjobs
    if (scheduler.isInstanceOf[LsfScheduler]) {
      Console.err.print("Initializing LSF monitoring...")
      Thread.sleep(3000)
      Console.err.println("Done")
    }

    requireListFile()

    updateNonFinalStats()

    while (isRunning) Thread.sleep(sleepInterval)

    // print a short report
    Console.err.println(file.name + ":" + statusReport)
  }


  private def updateNonFinalStats(): Unit = {
    // update runinfo for all jobs for which are not queued but are habe not yet reached a final statue
    val queueIds = queueStatus.map(_.jobId)
    val unqeuedNonFinalJobs = jobs.filterNot(_.isFinal).filterNot(j => queueIds.contains(j.id))
    unqeuedNonFinalJobs.foreach(updateStatsFile)
  }


  //
  // List Manipulation
  //


  def run(jc: JobConfiguration) = {
    val namedJC = jc.withName() // // fix auto-build a job configuration names if empty

    val jobId = scheduler.submit(namedJC)

    require(jobs.forall(_.config.name != namedJC.name), "job names must be unique")

    add(jobId)

    // serialzie job configuration in case we need to rerun it
    namedJC.saveAsXml(jobId, logsDir)

    Job(jobId)
  }


  /** Additional interface for non-restorable jobs. JL can still monitor those and do some list manipulation, but not
    * resubmit them.
    */
  def add(jobId: Int) = {
    file.appendLine(jobId + "")
    updateStatsFile(Job(jobId))

    Console.err.println(s"${file.name}: Added job '${jobId}'")
  }


  /** Convenience wrapper only. */
  //tbd needed?
  def resubmitKilled(resubStrategy: ResubmitStrategy = new TryAgain()) = resubmit(getConfigRoots(killed), resubStrategy)


  def resubmit(resubJobs: List[Job], resubStrategy: ResubmitStrategy = new TryAgain()): Unit = {
    // tbd consider to move/rename user-logs

    require(
      resubJobs.forall(_.isRestoreable),
      "joblist can only be resubmitted automatically only if all contained jobs were all submitted via `jl submit`"
    )

    // Make sure that same jc's are not submitted again while still running
    require(
      inQueue.map(_.config.name).strictIntersect(resubJobs.map(_.config.name)).isEmpty,
      s"jobs can not be resubmitted while still running: $resubJobs")


    // remove existing job instances with same job name from the list
    val otherJobs = {
      val resubConfigs = resubJobs.map(_.name)
      jobs.filterNot(j => resubConfigs.contains(j.name))
    }

    //todo we should use an intermediate temp-list and replace the old one just incase the resubmission is successful

    file.write("") // reset the file

    // readd successfully completed jobs
    otherJobs.foreach(job => file.appendLine(job.id + ""))


    // add resubmit killed ones and add their ids to the list-file as well
    Console.err.println(s"${file.name}: Resubmitting ${resubJobs.size} killed job${if (resubJobs.size > 1) "s" else ""} with ${resubStrategy}...")

    val prev2NewIds = resubJobs.map(job => job -> {
      run(resubStrategy.escalate(job.config))
    }).toMap

    // keep track of which jobs have been resubmitted by writing a graph file
    prev2NewIds.foreach { case (failedJob, resubJob) => resubGraphFile.appendLine(failedJob.id + "\t" + resubJob.id) }

    require(jobs.map(_.config.name).distinct.size == jobs.size, "Inconsistent sate. Each job name should appear just once per joblist")
  }


  def reset(): Unit = {
    file.delete(true)
    resubGraphFile.delete(true)

    //tbd why not just renaming them by default and have a wipeOut argument that would also clean up .jl files
  }


  def btop() = jobs.map(job => s"btop ${job.id}").foreach(Bash.eval)


  def kill() = jobs.map(job => s"bkill ${job.id}").foreach(Bash.eval)


  //
  //  Reporting
  //

  def statusReport: String = {
    if (!file.isRegularFile) {
      return s"${file.name} has not been initialized by adding a job to it"
    }

    // todo maybe we should refresh stats since some jobs might still be in the queue and it's not clear if jl is running

    val queuedJobs = queueStatus
    val numRunning = queueStatus.count(_.status == "RUN")
    val pending = queuedJobs.size - numRunning

    assert(queuedJobs.size + jobs.count(_.isFinal) == jobs.size)

    f" ${jobs.size}%4s jobs in total; ${jobs.size - failed.size}%4s done; ${numRunning}%4s running; ${pending}%4s pending; ; ${killed.size}%4s killed; ${failed.size}%4s failed; ${resubGraph().size}%4s ressubmitted"
  }


  /** Returns the graph of job resubmission (due to cluster resource limitations (ie walltime hits) or because the user
    * enforced it).  */
  def resubGraph(): Map[Job, Job] = {
    if (!resubGraphFile.isRegularFile) return Map()

    resubGraphFile.allLines.map(line => {
      val resubEvent = line.split("\t").map(_.toInt)
      (Job(resubEvent(0)), Job(resubEvent(1)))
    }).toMap
  }


  def exportStatistics(statsBaseFile: File = File(file.fullPath + ".stats")) = {
    requireListFile()

    val statsFile = File(statsBaseFile.fullPath + ".runinfo.log")

    statsFile.write(Seq("job_id", "job_name", "queue", "submit_time", "start_time", "finish_time",
      "queue_killed", "exec_host", "status", "user", "resubmission_of").mkString("\t"))
    statsFile.appendNewLine()


    val allIds: List[Int] = List.concat(jobs, resubGraph().keys).map(_.id)

    allIds.map(Job(_).info).
      map(ri => {
        Seq(
          ri.jobId, ri.jobName, ri.queue, ri.submitTime, ri.startTime, ri.finishTime,
          ri.queueKilled, ri.execHost, ri.status, ri.user, Job(ri.jobId).resubOf.map(_.id).getOrElse("")
        ).mkString("\t")
      }).foreach(statsFile.appendLine)


    // also write congig header where possible
    val jcLogFile = File(statsBaseFile.fullPath + ".jc.log")
    jcLogFile.write(
      Seq("id", "name", "num_threads", "other_queue_args", "queue", "wall_time", "wd").mkString("\t")
    )
    jcLogFile.appendNewLine()


    //noinspection ConvertibleToMethodValue
    val allJC = allIds.map(Job(_)).filter(_.isRestoreable).map(job => job -> job.config).toMap

    allJC.map({ case (job, jc) =>
      Seq(job.id, jc.name, jc.numThreads, jc.otherQueueArgs, jc.queue, jc.wallTime, jc.wd).mkString("\t")
    }).foreach(jcLogFile.appendLine)
  }


  //
  // Internal helpers
  //

  // todo require that we have stats for finished jobs


  def requireListFile() = require(file.isRegularFile && file.allLines.nonEmpty, s"job list '${file}'does not exist or is empty")


  override def toString: String = {
    s"""JobList(${file.name}, scheduler=${scheduler.getClass.getSimpleName}, wd=${file.fullPath}) :,
    $statusReport"""
  }


  def logsDir = (file.parent / ".jl").createIfNotExists(true)


  /** Update the job statistics. This won't update data for final jobs. */
  private def updateStatsFile(job: Job) = if (!job.isFinal) scheduler.updateRunInfo(job.id, job.infoFile)
}


case class Job(id: Int)(implicit val jl: JobList) {


  val infoFile = jl.logsDir / s"$id.runinfo"


  def info = {
    try {
      jl.scheduler.parseRunInfo(infoFile)
    } catch {
      case t: Throwable => throw new RuntimeException(s"could not readinfo for $id", t)
    }
  }


  def wasKilled = info.queueKilled


  def hasFailed = info.status == "EXIT"


  def isDone = info.status == "DONE"


  def isFinal: Boolean = infoFile.isRegularFile && List("EXIT", "DONE").contains(info.status)


  // todo actually this could be a collection of jobs because we escalate the base configuration
  // furthermore not job-id are resubmitted but job configuration, so the whole concept is flawed
  def resubAs() = {
    jl.resubGraph().find({ case (failed, resub) => resub == this }).map(_._2)
  }


  lazy val resubOf = {
    jl.resubGraph().find({ case (failed, resub) => resub == this }).map(_._1)
  }


  // note just use lazy val for properties that do not change

  lazy val isRestoreable = JobConfiguration.jcXML(id, jl.logsDir).isRegularFile


  lazy val config = {
    JobConfiguration.fromXML(id, jl.logsDir)
  }

  lazy val name = {
    JobConfiguration.fromXML(id, jl.logsDir).name
  }
}