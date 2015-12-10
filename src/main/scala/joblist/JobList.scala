package joblist

import better.files.File
import joblist.local.LocalScheduler
import joblist.lsf.LsfScheduler

import scalautils.Bash
import scalautils.CollectionUtils.StrictSetOps

/**
  * A stateless wrapper around a list of cluster jobs. The actual data is stored in a plain text-file containing
  * the job-IDs (one per line).
  *
  * By design JobList instances are invalid until a job is added to them (see <code>.requireListFile</code>)
  *
  * @author Holger Brandl
  */

case class JobList(file: File = File(".joblist"), scheduler: JobScheduler = guessScheduler()) extends AnyRef {


  def this(name: String) = this(File(name))


  implicit val JobList = this


  val resubGraphFile = File(file.fullPath + ".resubgraph")

  //
  // Model
  //

  /** Loads the current state of this joblist from disk. If there is no list files yet, it returns an empty list. */
  // Note: use cached .jobs instead
  private def ids = if (file.isRegularFile) file.allLines.map(_.toInt) else List()


  private var lastFileMD5: String = null
  private var cache: List[Job] = List()


  def jobs: List[Job] = {
    if (needsCacheUpdate()) {
      //      Console.err.println("refreshing jobs cache")
      cache = ids.map(Job(_))
    }

    cache
  }


  private def needsCacheUpdate(): Boolean = {
    // neccessary to emtpy cache after .reset
    if (!file.isRegularFile) {
      return true
    }

    // not using modificated time seems broken since it does not update fast enough
    val md5sum = file.md5
    if (md5sum != lastFileMD5) {
      lastFileMD5 = md5sum
      true
    } else {
      false
    }
  }


  //
  // Monitoring and Stats
  //


  def killed = {
    jobs.filter(_.wasKilled)
  }


  /** True if all jobs in the list are final and complete. */
  def isDone = {
    jobs.forall(_.isDone)
  }


  def failed = {
    /* see man bjobs for exit code: The job has terminated with a non-zero status – it may have been aborted due
     to an error in its execution, or killed by its owner or the LSF administrator */
    jobs.filter(_.hasFailed)
  }


  def requiresRerun = {
    jobs.filter(_.requiresRerun)
  }


  def queueStatus = {
    //noinspection ConvertibleToMethodValue
    val jobIds = jobs.map(_.id) // inlining ot would cause it to be evaulated jl.size times
    scheduler.getQueued.filter(qs => jobIds.contains(qs.jobId))
  }


  def inQueue = queueStatus.map(qs => Job(qs.jobId))


  private var lastQueueStatus: List[QueueStatus] = List()


  def isRunning: Boolean = {
    val nowInQueue = queueStatus

    // calculate a list of jobs for which the queue status changed. This basically an xor-set operation
    // (for which there's not library method)
    // this will detect changed status as well as presence/absence

    // note this type of xor is tested in joblist/TestTasks.scala:190
    val changedQS: Seq[Job] = nowInQueue.strictXor(lastQueueStatus).map(_.jobId).distinct.map(Job(_))

    // update runinfo for all jobs for which the queuing status has changed
    changedQS.foreach(updateStatsFile)

    //      println(s"${file.name}: In queue are ${nowInQueue.size} jobs out of ${jobs.size}")
    // workaround (fix?) for https://github.com/holgerbrandl/joblist/issues/5
    if (changedQS.nonEmpty) {
      printStatus()
      //todo  print estimated runtime here (see https://github.com/holgerbrandl/joblist/issues/9)
      // maybe check stdin and reprint report on user input???
    }


    // update last status to prepare for next iteration
    lastQueueStatus = nowInQueue

    nowInQueue.nonEmpty
  }


  def waitUntilDone(sleepInterval: Long = 10000): Any = {
    // stop if all jobs are final already
    if (jobs.forall(_.isFinal)) {
      return
    }

    // add delay because sometimes it takes a few seconds until jobs show up in bjobs
    if (scheduler.isInstanceOf[LsfScheduler]) {
      Console.out.print("Initializing LSF monitoring...")
      Thread.sleep(3000)
      Console.out.println("Done")
    }

    requireListFile()

    updateNonFinalStats()

    lastQueueStatus = List() //reset the queue history tracker

    while (isRunning) Thread.sleep(sleepInterval)

    // print a short report
    //    Console.err.println(file.name + ":" + status)
  }


  /** Update runinfo for all jobs for which are not queued anymore, but have not yet reached a final state.
    * This could happen for jobs which died too quickly  before we could gather stats about them
    */
  private def updateNonFinalStats(): Unit = {
    //    if(scheduler.isInstanceOf[LocalScheduler]) return

    val queueIds = queueStatus.map(_.jobId)
    val unqeuedNonFinalJobs = jobs.filterNot(_.isFinal).filterNot(j => queueIds.contains(j.id))


    //    if (unqeuedNonFinalJobs.nonEmpty && scheduler.isInstanceOf[LocalScheduler]) {
    //      Console.err.println("skipping info update for non-final jobs because local scheduler is used")
    //      return
    //    }

    unqeuedNonFinalJobs.foreach(updateStatsFile)
  }


  //
  // List Manipulation
  //


  /** Simple convenience wrapper around .run to submit multiple jobs. */
  def run(jobConfigs: Seq[JobConfiguration]): Seq[Job] = {
    jobConfigs.map(run)
  }


  def run(jc: JobConfiguration): Job = {
    val namedJC = jc.withName() // // fix auto-build a job configuration names if empty

    // create hidden log directory and log cmd as well as queuing args
    require(jc.wd.isDirectory)

    val jobLogs = namedJC.logs
    jobLogs.logsDir.createIfNotExists(true)

    val jobId = scheduler.submit(namedJC)

    // save user logs
    jobLogs.id.write(jobId + "")
    jobLogs.cmd.write(jc.cmd)


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

    // update is not feasible for actual scheduler which have some delay between submission and the job showing up in
    // the stats
    if (scheduler.isInstanceOf[LocalScheduler]) {
      updateStatsFile(Job(jobId))
    }


    Console.out.println(s"${file.name}: Added job '${jobId}'")
  }



  /** Waits for current jl to finish, resubmit failed jobs, wait again */
  def waitResubWait(resubmitStrategy: ResubmitStrategy = new TryAgain()) = {
    waitUntilDone()
    resubmit(resubmitStrategy)
    waitUntilDone()
  }


  /** Resubmit jobs to the queue. By default those jobs will be resubmitted which reached a final state that is
    * different from DONE. */
  def resubmit(resubStrategy: ResubmitStrategy = new TryAgain(),
               resubJobs: List[Job] = getConfigRoots(requiresRerun)): Unit = {

    // tbd consider to move/rename user-logs
    if (resubJobs.isEmpty) return // can happen when rerunning wait with local scheduler or finished joblist

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
    Console.out.println(s"${file.name}: Resubmitting ${resubJobs.size} job${if (resubJobs.size > 1) "s" else ""} with ${resubStrategy}...")

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
    // todo glob and delete  all instance related file file.name* --> rm

    file.parent.glob(s"${file.name}*").foreach(_.delete())

    //tbd why not just renaming them by default and have a wipeOut argument that would also clean up .jl files
  }


  def btop() = {
    require(scheduler.isInstanceOf[LsfScheduler], "btop is currently just supported for lsf")
    jobs.map(job => s"btop ${job.id}").foreach(Bash.eval(_))
  }


  def kill() = scheduler.cancel(jobs.map(_.id))


  //
  //  Reporting
  //

  def printStatus() = {
    println(status)
  }


  def status = {
    // we refresh stats here since some jobs might still be in the queue and it's not clear if jl is running

    if (!scheduler.isInstanceOf[LocalScheduler]) {
      updateNonFinalStats()
    }

    new ListStatus(this)
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


  //
  // Internal helpers
  //


  def requireListFile() = require(file.isRegularFile && file.allLines.nonEmpty, s"job list '${file}'does not exist or is empty")


  override def toString: String = {
    s"""JobList(${file.name}, scheduler=${scheduler.getClass.getSimpleName}, wd=${file.parent.fullPath})"""
  }


  def logsDir = (file.parent / ".jl").createIfNotExists(true)


  /** Update the job statistics. This won't update data for final jobs. */
  private def updateStatsFile(job: Job) = if (!job.isFinal) scheduler.updateRunInfo(job.id, job.infoFile)
}
