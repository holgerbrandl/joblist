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


  val resubGraphFile = File(file.fullPath + ".resubgraph")

  //
  // Model
  //

  /** Loads the current state of this joblist from disk. If there is no list files yet, it returns an empty list. */
  private def ids = if (file.isRegularFile) file.allLines.map(_.toInt) else List()


  def jobs = ids.map(Job(_))


  //
  // Monitoring and stats
  //


  def killed = {
    jobs.filter(_.wasKilled)
  }


  def failed = {
    /* see man bjobs for exit code: The job has terminated with a non-zero status – it may have been aborted due
     to an error in its execution, or killed by its owner or the LSF administrator */
    jobs.filter(_.info.status == "EXIT")
  }


  def inQueue = {
    //noinspection ConvertibleToMethodValue
    scheduler.getRunning.intersect(ids).map(Job(_))
  }


  def isRunning: Boolean = {
    Thread.sleep(2000) // because sometimes it takes a few seconds until jobs show up in bjobs

    val nowInQueue = inQueue

    // fetch final status for all those which are done now (for slurm do this much once the loop is done) and
    // for which we don't have final stats yet
    val noLongerInQueue = jobs.diff(nowInQueue)

    // write log file for already finished jobs (because  bjobs will loose history data soon)
    jobs.filter(job => noLongerInQueue.contains(job)).foreach(updateStatsFile)

    println(s"${file.name}: In queue are ${nowInQueue.size} jobs out of ${ids.size}")

    nowInQueue.nonEmpty
  }


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


  //
  // List Manipulation
  //

  def run(jc: JobConfiguration) = {
    val jobId = scheduler.submit(jc)

    require(jobs.forall(_.config.name != jc.name), "job names must be unique")

    add(jobId)

    // serialzie job configuration in case we need to rerun it
    jc.saveAsXml(jobId, logsDir)

    Job(jobId)
  }


  /** Addtional interface for non-restorable jobs. JL can still monitor those and do some list manipulation, but not
    * resubmit them.
    */
  def add(jobId: Int) = {
    file.appendLine(jobId + "")
    updateStatsFile(Job(jobId))

    Console.err.println(s"${file.name}: Added job '${jobId}'")
  }


  /** Derives a new job-list for just the killed jobs */
  def resubmitKilled(resubStrategy: ResubmitStrategy = new TryAgain(), useRoocJC: Boolean = true) = {
    // todo also provide means to retry failed jobs (because of user logic)
    var toBeResubmitted = killed

    // tbd consider to move/rename user-logs

    // optionally (and by default) we should use apply the original job configurations for escalation and resubmission?
    if (useRoocJC) {
      def findRootJC(job: Job): Job = {
        job.resubOf() match {
          case Some(rootJob) => findRootJC(rootJob)
          case None => job
        }
      }

      toBeResubmitted = toBeResubmitted.map(findRootJC)
    }

    resubmit(toBeResubmitted, resubStrategy)
  }


  def resubmit(resubJobs: List[Job], resubStrategy: ResubmitStrategy = new TryAgain()): Unit = {

    require(
      resubJobs.forall(_.isRestoreable),
      "joblist can only be resubmitted automatically only if all contained jobs were all submitted via `jl submit`"
    )

    // Make sure that same jc's are not submitted again while still running
    require(
      inQueue.map(_.config.name).intersect(resubJobs.map(_.config.name)).isEmpty,
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


  def reset() = {
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
    requireListFile()

    s" ${jobs.size} jobs in total; ${jobs.size - failed.size} done; ${killed.size} killed; ${resubGraph().size} ressubmitted"
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
      "exceeded_wall_limit", "exec_host", "status", "user", "resubmission_of").mkString("\t"))
    statsFile.appendNewLine()


    val allIds: List[Int] = List.concat(jobs, resubGraph().keys).map(_.id)

    allIds.map(Job(_).info).
      map(ri => {
        Seq(
          ri.jobId, ri.jobName, ri.queue, ri.submitTime, ri.startTime, ri.finishTime,
          ri.exceededWallLimit, ri.execHost, ri.status, ri.user, Job(ri.jobId).resubOf().map(_.id).getOrElse("")
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


  def requireListFile() = require(file.isRegularFile, s"job list '${file}'does not exist")


  override def toString: String = {
    s"JobList(${file.name}, scheduler=${scheduler.getClass.getSimpleName}, status={$statusReport})"
  }


  def logsDir = (file.parent / ".jl").createIfNotExists(true)


  private def updateStatsFile(job: Job) = if (!job.isDone) scheduler.updateRunInfo(job.id, job.infoFile)
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


  // todo actually this could be a collection of jobs because we escalate the base configuration
  // furthermore not job-id are resubmitted but job configuration, so the whole concept is flawed
  def resubAs() = {
    jl.resubGraph().find({ case (failed, resub) => resub == this }).map(_._2)
  }


  def resubOf() = {
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