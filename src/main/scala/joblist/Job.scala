package joblist

/**
  * @author Holger Brandl
  */
case class Job(id: Int)(implicit val jl: JobList) {


  val infoFile = jl.logsDir / s"$id.runinfo"


  def info = {
    try {
      jl.scheduler.parseRunInfo(infoFile)
    } catch {
      case t: Throwable => throw new RuntimeException(s"could not readinfo for $id", t)
    }
  }


  def wasKilled = info.queueKilled


  def hasFailed = info.status == "EXIT"


  def isDone = info.status == "DONE"


  def isFinal: Boolean = infoFile.isRegularFile && List("EXIT", "DONE").contains(info.status)


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
