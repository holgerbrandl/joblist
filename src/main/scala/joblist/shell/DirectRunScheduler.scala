package joblist.shell

import better.files.File
import joblist.{JobConfiguration, JobScheduler, RunInfo}


/**
  * Document me!
  *
  * @author Holger Brandl
  */
class DirectRunScheduler extends JobScheduler {

  /** Submits a job and returns its jobID. */
  override def submit(jc: JobConfiguration): Int = ???


  override def readRunLog(runinfoFile: File): RunInfo = ???


  override def updateRunInfo(id: Int, runinfoFile: File): Unit = ???


  override def getRunning: List[Int] = ???

  override def readIdsFromStdin(): List[Int] = ???
}
