package joblist

import joblist.PersistUtils._
import org.joda.time.DateTime

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


  /** Job reached final state but is not done (because it either failed, was killed or canceled) */
  def requiresRerun = infoFile.isRegularFile && isFinal && !isDone


  /** Update the job run statistics. This will silently skip over final runinfo */
  def updateStatsFile() = if (!isFinal) jl.scheduler.updateRunInfo(id, infoFile)


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

  case object CANCELLED extends JobState

  case object FAILED extends JobState

  case object KILLED extends JobState

  case object COMPLETED extends JobState

  case object UNKNOWN extends JobState

  val allStates = Seq(RUNNING, PENDING, CANCELLED, FAILED, KILLED, COMPLETED)
  val finalStates = List(CANCELLED, FAILED, KILLED, COMPLETED)


  def valueOf(status: String) = allStates.find(_.toString == status).getOrElse(UNKNOWN)

}

object Test extends App{
//  println(JobState.PENDING.toString)
//  println(JobState.PENDING.getClass.getName)

  private val info: RunInfo = new RunInfo(1, "me", JobState.PENDING, "long", "n22", "no_name", new DateTime, new DateTime, new DateTime, 3, "no_cause")

  private val xml: String = PersistUtils.getXstream.toXML(info)
  PersistUtils.getXstream.getConverterLookup
  println(xml)

  private val restoredInfo: AnyRef = PersistUtils.getXstream.fromXML(xml)
  println(restoredInfo)



//  private val restoredInfo2: AnyRef = joblist.getXstream.fromXML("""<RunInfo>
//                                                                   |  <jobId>79373156</jobId>
//                                                                   |  <user>brandl</user>
//                                                                   |  <state class="joblist.JobState$COMPLETED$"/>
//                                                                   |  <queue>local</queue>
//                                                                   |  <execHost>localhost</execHost>
//                                                                   |  <jobName>null_batch2</jobName>
//                                                                   |  <submitTime>29-04-2016 15:13:36</submitTime>
//                                                                   |  <startTime>29-04-2016 15:13:39</startTime>
//                                                                   |  <finishTime>29-04-2016 15:13:39</finishTime>
//                                                                   |  <exitCode>2147483647</exitCode>
//                                                                   |  <killCause></killCause>
//                                                                   |</RunInfo>""")
//
//  println(restoredInfo2) // so its not backwards compatible
}
