package joblist.shell

import better.files.File
import joblist.utils.RunLog
import joblist.{JobConfiguration, JobScheduler}

/**
  * Document me!
  *
  * @author Holger Brandl
  */
class ShellScheduler extends JobScheduler {

  /** Submits a job and returns its jobID. */
  override def submit(jc: JobConfiguration): Int = ???


  override def readRunLog(runinfoFile: File): RunLog = ???


  override def updateRunInfo(id: Int, runinfoFile: File): Unit = ???


  override def getRunning: List[Int] = ???
}
