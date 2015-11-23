package joblist

import better.files.File

import scalautils.Bash

/**
  * A stateless wrapper around list of cluster jobs. The actual data is stoed in a plain text-file containing the job-IDs (one per line).
  *
  * @author Holger Brandl
  */

case class JobList(file: File = File(".joblist"), scheduler: JobScheduler = guessQueue()) extends AnyRef {


  def this(name: String) = this(File(name))

  //
  // Model
  //

  def run(jc: JobConfiguration) = {
    val jobId = scheduler.submit(jc)

    add(jobId)

    // serialzie job configuration in case we need to rerun it
    jc.saveAsXml(jobId, logsDir)

    jobId
  }


  def add(jobId: Int) = {
    file.appendLine(jobId + "")
    updateStatsFile(Job(jobId))
  }


  case class Job(id: Int) {

    def isDone: Boolean = infoFile.isRegularFile && info.isDone


    val infoFile = logsDir / s"$id.runinfo"


    def info = scheduler.readRunLog(infoFile)
  }


  def jobs = jobIds.map(Job)


  //
  // Monitoring and stats
  //

  def isRunning: Boolean = {
    Thread.sleep(2000) // because sometimes it takes a few seconds until jobs show up in bjobs

    val inQueue = scheduler.getRunning


    // fetch final status for all those which are done now (for slurm do this much once the loop is done) and
    // for which we don't have final stats yet
    val alreadyDone = jobIds.diff(inQueue)

    // write log file for already finished jobs (because  bjobs will loose history data soon)
    jobs.filter(job => alreadyDone.contains(job.id)).foreach(updateStatsFile)

    println(s"$file: Remaining ${inQueue.intersect(jobIds).size} jobs out of ${jobIds.size}")

    inQueue.intersect(jobIds).nonEmpty
  }


  //
  // Commands
  //

  def btop() = jobs.map(job => s"btop ${job.id}").foreach(Bash.eval)


  def kill() = jobs.map(job => s"bkill ${job.id}").foreach(Bash.eval)


  def waitUntilDone(msg: String = "", sleepInterval: Long = 10000) = {
    //    require(file.isRegularFile && file.lines.nonEmpty, s"joblist '$file' is empy")
    if (!file.isRegularFile || file.lines.isEmpty) {
      throw new RuntimeException(s"Error: There is no valid joblist named ${this.file.path}")
    }

    while (isRunning) Thread.sleep(sleepInterval)

    // print a short report
    Console.err.println(statusReport)
  }


  def statusReport: String = {
    s"${file.name} complete (${jobs.size - killed.size} done; ${killed.size} killed)"
  }


  /** Derives a new job-list for just the killed jobs */
  def resubmitKilled(resubStrategy: ResubmitStrategy = new TryAgain()) = {
    // todo also provide means to retry failed jobs (because of user logic)
    val failedJobs: Map[Int, JobConfiguration] = jobConfigs.filterKeys(killed.contains)

    // remove killed ids from list file
    file.write(jobIds.diff(failedJobs.keys.toList).mkString("\n"))
    file.appendNewLine()

    // tbd maybe we should rather apply the escalate to the root jc?

    val killed2resubIds = failedJobs.mapValues(jc => {
      run(resubStrategy.escalate(jc))
    })

    Console.err.println(s"${file.name}: Resubmitting ${failedJobs.size} with ${resubStrategy}...")

    // keep track of which jobs have been resubmitted by writing a graph file
    killed2resubIds.foreach { case (oldId, resubId) => resubGraphFile.appendLine(oldId + "\t" + resubId) }

    // tbd consider to move/rename user-logs
  }


  val resubGraphFile = File(file.fullPath + ".resubgraph")

  def reset() = {
    file.delete(true)
    resubGraphFile.delete(true)
  }


  def killed = {
    jobs.filter(_.info.queueKilled).map(_.id)
  }

  /** Returns the graph of job resubmission due to cluster resource limitations (ie walltime hits).  */
  private def resubGraph() = {
    resubGraphFile.lines.map(line => {
      val resubEvent = line.split("\t").map(_.toInt)
      (Job(resubEvent(0)), Job(resubEvent(1)))
    })
  }


  def failed = {
    /* see man bjobs for exit code: The job has terminated with a non-zero status – it may have been aborted due
     to an error in its execution, or killed by its owner or the LSF administrator */
    jobs.filter(_.info.status == "EXIT").map(_.id)
  }


  /** get all job configurations associated to the joblist */
  def jobConfigs = {
    jobIds.map(jobId => jobId -> restoreConfig(jobId)).toMap
  }


  def restoreConfig(jobId: Int) = {
    JobConfiguration.fromXML(jobId, logsDir)
  }


  //
  // Internal helpers
  //

  // todo require that we have stats for finished jobs

  def logsDir = (file.parent / ".jl").createIfNotExists(true)


  def jobIds = if (file.isRegularFile) file.lines.map(_.toInt).toList else List()


  private def updateStatsFile(job: Job): Any = {
    if (job.isDone) {
      return
    }

    scheduler.updateRunInfo(job.id, job.infoFile)
  }
}

