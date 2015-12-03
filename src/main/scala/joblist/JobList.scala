package joblist

import better.files.File
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
      println(file.name + ":" + statusReport)
      //todo  print estimated runtime here (see https://github.com/holgerbrandl/joblist/issues/9)
      // maybe check stdin and reprint report on user input???
    }


    // update last status to prepare for next iteration
    lastQueueStatus = nowInQueue

    nowInQueue.nonEmpty
  }


  def waitUntilDone(msg: String = "", sleepInterval: Long = 10000): Any = {
    // stop if all jobs are final already
    if (jobs.forall(_.isFinal)) {
      return
    }

    // add delay because sometimes it takes a few seconds until jobs show up in bjobs
    if (scheduler.isInstanceOf[LsfScheduler]) {
      Console.err.print("Initializing LSF monitoring...")
      Thread.sleep(3000)
      Console.err.println("Done")
    }

    requireListFile()

    updateNonFinalStats()

    lastQueueStatus = List() //reset the queue history tracker

    while (isRunning) Thread.sleep(sleepInterval)

    // print a short report
    Console.err.println(file.name + ":" + statusReport)
  }


  /** Update runinfo for all jobs for which are not queued anylonger buthave not yet reached a final state */

  private def updateNonFinalStats(): Unit = {
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
    //    updateStatsFile(Job(jobId))

    Console.err.println(s"${file.name}: Added job '${jobId}'")
  }


  /** Waits for current jl to finish, resubmit failed jobs, wait again */
  def waitResubWait(resubmitStrategy: ResubmitStrategy = new TryAgain()) = {
    waitUntilDone()
    resubmitFailed(resubmitStrategy)
    waitUntilDone()
  }


  def resubmitFailed(resubStrategy: ResubmitStrategy = new TryAgain(),
                     resubJobs: List[Job] = getConfigRoots(failed)): Unit = {

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
    // todo glob and delete  all instance related file file.name* --> rm

    //tbd why not just renaming them by default and have a wipeOut argument that would also clean up .jl files
  }


  // todo btop and bkill are lsf only and should be refactored to become scheduler API
  def btop() = jobs.map(job => s"btop ${job.id}").foreach(Bash.eval(_))


  def kill() = jobs.map(job => s"bkill ${job.id}").foreach(Bash.eval(_))


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

    // also do some internal consistenct checks
    assert(queuedJobs.size + jobs.count(_.isFinal) == jobs.size)
    assert(jobs.nonEmpty)

    f" ${jobs.size}%4s jobs in total; ${jobs.count(_.isDone)}%4s done; ${numRunning}%4s running; ${pending}%4s pending; ; ${killed.size}%4s killed; ${failed.size}%4s failed; ${resubGraph().size}%4s ressubmitted"
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
    s"""JobList(${file.name}, scheduler=${scheduler.getClass.getSimpleName}, wd=${file.fullPath})"""
  }


  def logsDir = (file.parent / ".jl").createIfNotExists(true)


  /** Update the job statistics. This won't update data for final jobs. */
  private def updateStatsFile(job: Job) = if (!job.isFinal) scheduler.updateRunInfo(job.id, job.infoFile)
}
