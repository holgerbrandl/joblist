package joblist

import better.files.File

/**
  * An interface used by jl to communicate with job schedulers
  *
  * @author Holger Brandl
  */
trait JobScheduler {

  /** Extracts job IDs from job submission from stdin. */
  def readIdsFromStdin(): List[Int]

  /** Submits a job and returns its jobID. */
  def submit(jc: JobConfiguration): Int

  /** Returns currently queued jobs of the users. */
  def getQueued: List[QueueStatus]

  /** Writes current the job statistics into the given file. */
  def updateRunInfo(jobId: Int, logFile: File): Unit

  /** Cancel a list of jobs */
  def cancel(jobIds: Seq[Int])
}


case class QueueStatus(jobId: Int, status: String)

