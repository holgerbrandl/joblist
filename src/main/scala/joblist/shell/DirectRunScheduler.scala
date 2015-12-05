package joblist.shell

import better.files.File
import joblist.{JobConfiguration, JobScheduler, QueueStatus}


/**
  * Document me!
  *
  * @author Holger Brandl
  */
class DirectRunScheduler extends JobScheduler {

  /** Submits a job and returns its jobID. */
  override def submit(jc: JobConfiguration): Int = ???


  override def updateRunInfo(id: Int, runinfoFile: File): Unit = ???


  override def getQueued: List[QueueStatus] = ???


  override def readIdsFromStdin(): List[Int] = ???
}
