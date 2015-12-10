package joblist

/**
  * @author Holger Brandl
  */
case class Job(id: Int)(implicit val jl: JobList) {


  val infoFile = jl.logsDir / s"$id.runinfo.xml"


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


  def isDone = info.state == JobState.COMPLETED


  def isFinal = infoFile.isRegularFile && JobState.finalStates.contains(info.state)


  /** Update the job run statistics. This will silently skip over final runinfo */
  def updateStatsFile() = if (!isFinal) jl.scheduler.updateRunInfo(id, infoFile)


  /** Job reached final state but is not done (because it either failed, was killed or canceled) */
  def requiresRerun = infoFile.isRegularFile && isFinal && !isDone


  // todo actually this could be a collection of jobs because we escalate the base configuration
  // furthermore not job-id are resubmitted but job configuration, so the whole concept is flawed
  def resubAs() = {
    jl.resubGraph().find({ case (failed, resub) => resub == this }).map(_._2)
  }


  lazy val resubOf = {
    jl.resubGraph().find({ case (failed, resub) => resub == this }).map(_._1)
  }


  // note just use lazy val for properties that do not change

  lazy val isRestoreable = JobConfiguration.jcXML(id, jl.logsDir).isRegularFile


  lazy val config = {
    JobConfiguration.fromXML(id, jl.logsDir)
  }

  lazy val name = {
    JobConfiguration.fromXML(id, jl.logsDir).name
  }
}


object JobState {


  sealed trait JobState

  case object RUNNING extends JobState

  case object PENDING extends JobState

  case object CANCELED extends JobState

  case object FAILED extends JobState

  case object KILLED extends JobState

  case object COMPLETED extends JobState

  case object UNKNOWN extends JobState

  val allStates = Seq(RUNNING, PENDING, CANCELED, FAILED, COMPLETED)
  val finalStates = List(CANCELED, FAILED, KILLED, COMPLETED)


  def valueOf(status: String) = allStates.find(_.toString == status).getOrElse(UNKNOWN)

}

