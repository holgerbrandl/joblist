package joblist

import better.files._





/** Log files that might be of interest for the users. JL does not rely on them. */
case class JobLogs(name: String, wd: File) {

  def logsDir = wd / s".logs"


  // file getters
  val id = logsDir / s"$name.jobid"
  val cmd = logsDir / s"$name.cmd"
  val err = logsDir / s"$name.err.log"
  val out = logsDir / s"$name.out.log"
}

