package joblist

import better.files.File

import scalautils.Bash

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
  //
  // Model
  //

  def run(jc: JobConfiguration) = {
    val jobId = scheduler.submit(jc)

    add(jobId)

    // serialzie job configuration in case we need to rerun it
    jc.saveAsXml(jobId, logsDir)

    Job(jobId)
  }


  def add(jobId: Int) = {
    file.appendLine(jobId + "")
    updateStatsFile(Job(jobId))

    Console.err.println(s"${file.name}: Added job '${jobId}'")
  }


  def jobs = jobIds.map(Job(_))


  //
  // Monitoring and stats
  //

  def isRunning: Boolean = {
    Thread.sleep(2000) // because sometimes it takes a few seconds until jobs show up in bjobs

    val inQueue = scheduler.getRunning

    // fetch final status for all those which are done now (for slurm do this much once the loop is done) and
    // for which we don't have final stats yet
    val noLongerInQueue = jobIds.diff(inQueue)

    // write log file for already finished jobs (because  bjobs will loose history data soon)
    jobs.filter(job => noLongerInQueue.contains(job.id)).
      //      filterNot(_.info.isDone).
      foreach(updateStatsFile)

    println(s"${file.name}: Remaining ${inQueue.intersect(jobIds).size} jobs out of ${jobIds.size}")

    inQueue.intersect(jobIds).nonEmpty
  }


  //
  // Commands
  //

  def btop() = jobs.map(job => s"btop ${job.id}").foreach(Bash.eval)


  def kill() = jobs.map(job => s"bkill ${job.id}").foreach(Bash.eval)


  def waitUntilDone(msg: String = "", sleepInterval: Long = 10000) = {
    requireListFile()

    //    require(file.isRegularFile && file.lines.nonEmpty, s"joblist '$file' is empy")
    if (!file.isRegularFile || file.allLines.isEmpty) {
      throw new scala.RuntimeException(s"Error: There is no valid joblist named ${this.file.path}")
    }

    while (isRunning) Thread.sleep(sleepInterval)

    // print a short report
    Console.err.println(file.name + ":" + statusReport)
  }


  def requireListFile() = require(file.isRegularFile, s"job list '${file}'does not exist")


  def statusReport: String = {
    requireListFile()

    s"complete (${jobs.size - killed.size} done; ${killed.size} killed)"
  }


  def exportStatistics(statsBaseFile: File = File(file.fullPath + ".stats")) = {
    requireListFile()

    val statsFile = File(statsBaseFile.fullPath + ".runinfo.log")

    statsFile.write(Seq("job_id", "job_name", "queue", "submit_time", "start_time", "finish_time",
      "exceeded_wall_limit", "exec_host", "status", "user", "resubmitted_as").mkString("\t"))
    statsFile.appendNewLine()


    val allIds: List[Int] = List.concat(jobs, resubGraph().keys).map(_.id)

    allIds.map(Job(_).info).
      map(ri => {
        Seq(
          ri.jobId, ri.jobName, ri.queue, ri.submitTime, ri.startTime, ri.finishTime,
          ri.exceededWallLimit, ri.execHost, ri.status, ri.user, Job(ri.jobId).resubAs().getOrElse("")
        ).mkString("\t")
      }).foreach(statsFile.appendLine)


    // also write congig header where possible
    val jcLogFile = File(statsBaseFile.fullPath + ".jc.log")
    jcLogFile.write(
      Seq("id", "name", "num_threads", "other_queue_args", "queue", "wall_time", "wd").mkString("\t")
    )
    jcLogFile.appendNewLine()


    val allJC = allIds.map(Job(_)).filter(_.isRestoreable).map(job => job -> job.config).toMap

    allJC.map({ case (job, jc) =>
      Seq(job.id, jc.name, jc.numThreads, jc.otherQueueArgs, jc.queue, jc.wallTime, jc.wd).mkString("\t")
    }).foreach(jcLogFile.appendLine)

    // todo optionally render html report
  }


  /** Derives a new job-list for just the killed jobs */
  // tbd do we actually need this convenience method?
  def resubmitKilled(resubStrategy: ResubmitStrategy = new TryAgain()) = {
    // todo also provide means to retry failed jobs (because of user logic)
    val killedJobs = killed

    resubmit(killedJobs, resubStrategy)

    // tbd consider to move/rename user-logs
  }


  def resubmit(resubJobs: List[Job], resubStrategy: ResubmitStrategy = new TryAgain()): Unit = {

    require(
      resubJobs.forall(_.isRestoreable),
      "joblist can only be resubmitted automatically only if all contained jobs were all submitted via `jl submit`"
    )

    // remove killed ids from list file
    val succeededJobs = jobs.diff(killed)

    file.write("") // reset the file

    // readd successfully completed jobs
    succeededJobs.foreach(job => file.appendLine(job.id + ""))

    // tbd maybe we should rather apply the escalate to the root jc?

    // add resubmit killed ones and add their ids to the list-file as well
    Console.err.println(s"${file.name}: Resubmitting ${resubJobs.size} killed job${if (resubJobs.size > 1) "s" else ""} with ${resubStrategy}...")

    val killed2resubIds = resubJobs.map(job => job -> {
      run(resubStrategy.escalate(job.config))
    }).toMap

    // keep track of which jobs have been resubmitted by writing a graph file
    killed2resubIds.foreach { case (failedJob, resubJob) => resubGraphFile.appendLine(failedJob.id + "\t" + resubJob.id) }
  }


  val resubGraphFile = File(file.fullPath + ".resubgraph")


  def reset() = {
    file.delete(true)
    resubGraphFile.delete(true)

    //tbd why not just renaming them by default and have a wipeOut argument that would also clean up .jl files
  }


  def killed = {
    jobs.filter(_.wasKilled)
  }


  /** Returns the graph of job resubmission due to cluster resource limitations (ie walltime hits).  */
  def resubGraph(): Map[Job, Job] = {
    if (!resubGraphFile.isRegularFile) return Map()

    resubGraphFile.allLines.map(line => {
      val resubEvent = line.split("\t").map(_.toInt)
      (Job(resubEvent(0)), Job(resubEvent(1)))
    }).toMap
  }


  def failed = {
    /* see man bjobs for exit code: The job has terminated with a non-zero status – it may have been aborted due
     to an error in its execution, or killed by its owner or the LSF administrator */
    jobs.filter(_.info.status == "EXIT")
  }


  /** get all job configurations associated to the joblist */
  @Deprecated // unclear need
  def jobConfigs = {
    jobs.map(job => job -> job.config).toMap
  }


  //
  // Internal helpers
  //

  // todo require that we have stats for finished jobs

  override def toString: String = {
    s"JobList(${file.name}, scheduler=${scheduler.getClass.getSimpleName}, status={$statusReport})"
  }


  def logsDir = (file.parent / ".jl").createIfNotExists(true)


  private def jobIds = if (file.isRegularFile) file.allLines.map(_.toInt).toList else List()


  private def updateStatsFile(job: Job): Any = {
    if (job.isDone) {
      return
    }

    scheduler.updateRunInfo(job.id, job.infoFile)
  }
}


case class Job(id: Int)(implicit val jl: JobList) {

  def isDone: Boolean = infoFile.isRegularFile && info.isDone


  val infoFile = jl.logsDir / s"$id.runinfo"


  def info = {
    try {
      jl.scheduler.readRunLog(infoFile)
    } catch {
      case t: Throwable => throw new RuntimeException(s"could not readinfo for $id", t)
    }
  }


  def wasKilled = info.queueKilled


  def resubAs(): Option[Job] = {
    jl.resubGraph().find({ case (failed, resub) => resub == this }).map(_._2)
  }


  def isRestoreable = JobConfiguration.jcXML(id, jl.logsDir).isRegularFile


  def config = {
    JobConfiguration.fromXML(id, jl.logsDir)
  }
}


