package joblist

import better.files.File
import joblist.JobState.JobState

/**
  * An interface used by jl to communicate with job schedulers
  *
  * @author Holger Brandl
  */
trait JobScheduler {

  /** Extracts job IDs from job submissions from stdin. */
  def readIdsFromStdin(): List[Int]

  /** Submits a job and returns its job ID. */
  def submit(jc: JobConfiguration): Int

  /** Returns currently queued jobs. */
  def getJobStates(jobIds: List[Int]): List[QueueStatus]

  /** Writes job statistics as XML serialized joblist.RunInfo into the given file. */
  def updateRunInfo(jobId: Int, logFile: File): Unit

  /** Cancel a list of jobs */
  def cancel(jobIds: Seq[Int])
}


case class QueueStatus(jobId: Int, state: JobState)

