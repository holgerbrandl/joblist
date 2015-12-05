package joblist

import better.files.File

/**
  * An abstract queueing system that allows for basic operations like quering the current queue, and getting job statistics
  *
  * @author Holger Brandl
  */
abstract class JobScheduler {

  def readIdsFromStdin(): List[Int]


  // Convenience method //tdb needed?
  def submit(cmd: String, name: String): Int = submit(JobConfiguration(cmd, name))


  /** Submits a job and returns its jobID. */
  def submit(jc: JobConfiguration): Int


  /** Returns currently queued jobs of the users. */
  def getQueued: List[QueueStatus]


  def getRunInfo(runinfoFile: File) = {
    fromXml(runinfoFile).asInstanceOf[RunInfo]
  }

  def updateRunInfo(jobId: Int, logFile: File): Unit
}

case class QueueStatus(jobId: Int, status: String)

