package scalautils.tasks

import better.files.File

import scalautils.tasks.Tasks.BashSnippet

;


/**
  * A list of cluster jobs implemented as a text-file containing the job-IDs.
  *
  * @author Holger Brandl
  */
case class JobList(file: File = File(".joblist")) extends AnyRef {

  def btop() = Bash.eval(s"cat ${file.fullPath} | xargs -L1 btop")


  def this(name: String) = this(File(name))


  def waitUntilDone(msg: String = "") = LsfUtils.wait4jobs(this.file)


  def killed = {
    val killedListFile = File(file.fullPath + ".killed_jobs.txt")
    require(killedListFile.isRegularFile)

    val killedJobs = killedListFile.lines.map(_.toInt)

    // find cmd-logs of killed jobs
    val isKilled: (File) => Boolean = jobIdFile => killedJobs.contains(jobIdFile.lines.mkString.toInt) // predicate function

    val logsDir = file.parent / ".logs"

    // build forward map
    val id2name = logsDir.
      glob("*.jobid").
      map(idFile => idFile.lines.mkString.toInt -> idFile.nameWithoutExtension).toMap

    val killedId2Names = id2name.filterKeys(killedJobs.contains(_))

    //  case class SnippetWithStatus(snippet:BashSnippet, prevJobId:Int)

    //  convert back to bash-snippets

    def restoreTaskFromLogs(jobname: String): BashSnippet = {
      (logsDir / jobname + ".cmd").lines.mkString("\n").toBash.inDir(logsDir.parent)
    }

    killedId2Names.map { case (jobid, jobname) => restoreTaskFromLogs(jobname) }
  }


  def reset = if (file.exists) file.delete()
}
