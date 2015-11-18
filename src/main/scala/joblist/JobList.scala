package joblist

import better.files.File

import scalautils.Bash



/**
  * A stateless wrapper around list of cluster jobs. The actual data is stoed in a plain text-file containing the job-IDs (one per line).
  *
  * @author Holger Brandl
  */

case class JobList(file: File = File(".joblist")) extends AnyRef {


  def this(name: String) = this(File(name))


  //
  // Model
  //

  def add(jobId: Int) = {
    file.appendLine(jobId + "")
    updateStatsFile(Job(jobId))
  }


  case class Job(id: Int) {

    def isDone: Boolean = runinfoFile.isRegularFile && info.isDone


    val runinfoFile = logsDir / s"$id.runinfo"


    def info = LsfUtils.readRunLog(runinfoFile)
  }


  def jobs = jobIds.map(Job)


  //
  // Monitoring and stats
  //

  def isRunning: Boolean = {
    Thread.sleep(2000) // because sometimes it takes a few seconds until jobs show up in bjobs

    val inQueue = Bash.eval("bjobs").stdout.drop(1).
      filter(!_.isEmpty).
      map(_.split(" ")(0).toInt).toList


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


  def waitUntilDone(msg: String = "", withReport: Boolean = false) = {
    //    require(file.isRegularFile && file.lines.nonEmpty, s"joblist '$file' is empy")
    if (file.isRegularFile && file.lines.nonEmpty) {
      Console.err.println(s"There is no joblist named ${this.file.path}")
    }

    while (isRunning) Thread.sleep(10000)
  }


  def reset() = if (file.exists) file.delete()


  def killed = {
    jobs.filter(_.info.exceededWallLimit).map(_.id)
  }


  def jobConfigs = {
    jobIds.map(jobId => jobId -> restoreConfig(jobId)).toMap
  }


  def restoreConfig(jobId: Int) = {
    LsfJobConfiguration.fromXML(jobId, file.parent)
  }


  //
  // Internal helpers
  //

  // todo require that we have stats for finished jobs

  def logsDir = (file.parent / ".jl").createIfNotExists(true)


  def jobIds = if (file.isRegularFile) file.lines.map(_.toInt).toList else List()


  private def updateStatsFile(job: Job): Any = {
    // don't replace existing final logs
    if (job.isDone) {
      return
    }

    // abstract queuing system here
    LsfUtils.updateRunInfo(job.id, job.runinfoFile)
  }



}
