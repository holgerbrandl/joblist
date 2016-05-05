package joblist

import java.text.DecimalFormat

import org.joda.time.format.ISOPeriodFormat
import org.joda.time.{DateTime, Duration, Seconds}

import scalautils.math._

/**
  * Created by brandl on 4/29/16.
  */
class ListStatus(jl: JobList) {

  // we snapshot queue and jobs here and reuse for all the counting
  val queueSnapshot = jl.queueStatus()
  val jobsSnapshot = jl.jobs

  //    // detail out jobs without runinfo (requires enable of joblist/lsf/LsfScheduler.scala:106)
  //    private val missingJobInfo: List[Job] = jobsSnapshot.filter(!_.infoFile.exists)
  //    if(missingJobInfo.nonEmpty){
  //      print(missingJobInfo.map(job => job.id + " " + job.name).mkString("\n"))
  //    }

  val numTotal = jobsSnapshot.size
  val numDone = jobsSnapshot.count(_.isCompleted)
  val numFinal = jobsSnapshot.count(_.isFinal)
  val numFailed = jobsSnapshot.count(_.hasFailed)
  val numKilled = jobsSnapshot.count(_.wasKilled)


  val numRunning = queueSnapshot.count(_.state == JobState.RUNNING)
  val numPending = queueSnapshot.size - numRunning // todo pending could also come from the queue info


  // ensure list consistency, but just warn because temoprary list incnsistencies can happen
  def hasConsistentCounts = queueSnapshot.size + numFinal != numTotal


  if (hasConsistentCounts) {
    Console.err.println(s"warning: job counts don t add up: ${toString}")
  }


  val finalPerc = numFinal.toDouble / jobsSnapshot.size


  def fixedLenFinalPerc = jobsSnapshot.size match {
    case 0 => " <NA>"
    case _ => "%5s" format new DecimalFormat("0.0").format(100 * finalPerc)
  }


  lazy val remTime = estimateRemainingTime(jobsSnapshot)


  def stringifyRemTime = remTime match {
    // http://stackoverflow.com/questions/3471397/pretty-print-duration-in-java
    case Some(duration) => "~" + ISOPeriodFormat.standard().print(duration.toPeriod).replace("PT", "")
    case _ => "<NA>"
  }


  override def toString = {
    val summary = f"$numTotal%4s jobs in total; $fixedLenFinalPerc%% complete; Remaining time $stringifyRemTime%10s; "
    val counts = f"$numDone%4s done; $numRunning%4s running; $numPending%4s pending; $numKilled%4s killed; $numFailed%4s failed"

    val unknownState = jobsSnapshot.filter(_.info.state == JobState.UNKNOWN)
    val unknownIfAny = if (unknownState.isEmpty) "" else s"""; unknown ${unknownState}={${unknownState.map(_.id).mkString(",")}"""

    summary + counts + unknownIfAny
  }

  def estimateRemainingTime(jobs: List[Job]): Option[Duration] = {

    // don't estimate if too few jobs provide data
    if (jobs.forall(_.isFinal)) return Some(Duration.ZERO)
    if (!jobs.exists(_.isFinal)) return None


    // calc mean runtime for all finished jobs
    val avgRuntimeSecs = jobs.filter(_.isFinal).map(_.info).map(ri => {
      new Duration(ri.startTime, ri.finishTime).getStandardSeconds.toDouble
    }).mean


    val numPending = jobs.count(_.info.state == JobState.PENDING)

    val startedJobs = jobs.filter(_.info.startTime != null)

    // if all jobs have been started take last one and calc expected remaining time
    if (numPending == 0) {
      def startDateSort(job: Job, other: Job) = job.info.startTime.isBefore(other.info.startTime)
      val lastStarter = startedJobs.sortWith(startDateSort).last
      val lsCurRuntime = new Duration(lastStarter.info.startTime, new DateTime()).getStandardSeconds

      // prevent negative time estimates and cap at 10seconds
      // todo unit test this
      return Some(Seconds.seconds(Math.max(avgRuntimeSecs - lsCurRuntime, 10).toInt).toStandardDuration)
    }

    // because we can not estimate a single diff between job starts
    if (startedJobs.size < 2) return None


    //calculate diffs in starting time to estimate avg between starts
    val avgStartDiffSecs = startedJobs.
      map(_.info.startTime).
      sortWith(_.isBefore(_)).
      //define a sliding windows of 2 subsequent start times and calculate differences
      sliding(2).
      map { case List(firstTime, sndTime) =>
        new Duration(firstTime, sndTime).getStandardSeconds.toDouble
      }.
      // just use the last 20 differences (because cluster load might change over time)
      //        toList.takeRight(20).median
      toList.takeRight(20).quantile(0.8)



    // basically runtime is equal as last jobs finishtime which can be approximated by
    val numSecondsRemaining = numPending * avgStartDiffSecs + avgRuntimeSecs
    Some(Seconds.seconds(numSecondsRemaining.round.toInt).toStandardDuration)
  }

}
