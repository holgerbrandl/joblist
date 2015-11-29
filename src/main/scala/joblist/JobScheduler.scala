package joblist

import better.files.File

/**
  * An abstract queueing system that allows for basic operations like quering the current queue, and getting job statistics
  *
  * @author Holger Brandl
  */
abstract class JobScheduler {

  def readIdsFromStdin(): List[Int]


  def submit(cmd: String, name: String): Int = submit(JobConfiguration(cmd, name))


  /** Submits a job and returns its jobID. */
  def submit(jc: JobConfiguration): Int


  def getQueued: List[QueueStatus]


  def parseRunInfo(runinfoFile: File): RunInfo


  def updateRunInfo(id: Int, runinfoFile: File): Unit
}

case class QueueStatus(jobId: Int, status: String)

