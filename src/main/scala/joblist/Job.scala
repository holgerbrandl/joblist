package joblist

import joblist.PersistUtils._
import org.joda.time.DateTime

/**
  * @author Holger Brandl
  */
case class Job(id: Int)(implicit val jl: JobList) {


  val infoFile = jl.dbDir / s"$id.runinfo.xml"


  def info = {
    try {
      val _info = fromXml(infoFile).asInstanceOf[RunInfo]

      require(_info.jobId == id, "Inconsistent run information")

      _info
    } catch {
      case t: Throwable => throw new RuntimeException(s"could not readinfo for $id", t)
    }
  }


  def wasKilled = info.state == JobState.KILLED

  def hasFailed = info.state == JobState.FAILED

  def isCompleted = info.state == JobState.COMPLETED

  def wasCancelled = info.state == JobState.CANCELLED

  def isFinal = infoFile.isRegularFile && JobState.finalStates.contains(info.state)


  /** Job reached final state but is not done (because it either failed, was killed or canceled) */
  def requiresRerun = infoFile.isRegularFile && isFinal && !isCompleted


  /** Update the job run statistics. This will silently skip over final runinfo */
  def updateStatsFile() = if (!isFinal) jl.scheduler.updateRunInfo(id, infoFile)


  /**
    * Convenience method to explore the resubmission graph. At the moment not used by joblist itself
    *
    * This approach is conceptually a bit flawed since not job-id are resubmitted but job configurations.
    *
    * @return a collection of jobs that were escalated from this jobs' base configuration
    */
  def resubmittedAs(includeChilds:Boolean=false): Iterable[Job] = {
    val resubmissions = jl.resubGraph().filter({ case (failed, resub) => resub == this }).values

    if(includeChilds)
      resubmissions.flatMap(_.resubmittedAs(true))
    else
      resubmissions
  }


  lazy val resubOf = {
    jl.resubGraph().find({ case (failed, resub) => resub == this }).map(_._1)
  }


  // note just use lazy val for properties that do not change

  lazy val isRestoreable = JobConfiguration.jcXML(id, jl.dbDir).isRegularFile


  lazy val config = {
    JobConfiguration.fromXML(id, jl.dbDir)
  }

  lazy val name = {
    JobConfiguration.fromXML(id, jl.dbDir).name
  }
}


object JobState {


  sealed trait JobState

  case object RUNNING extends JobState

  case object PENDING extends JobState

  case object CANCELLED extends JobState

  case object FAILED extends JobState

  case object KILLED extends JobState

  case object COMPLETED extends JobState

  case object UNKNOWN extends JobState

  val allStates = Seq(RUNNING, PENDING, CANCELLED, FAILED, KILLED, COMPLETED)
  val finalStates = List(CANCELLED, FAILED, KILLED, COMPLETED)


  def valueOf(status: String) = allStates.find(_.toString == status).getOrElse(UNKNOWN)

}