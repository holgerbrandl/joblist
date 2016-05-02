package joblist

import better.files.File

/**
  * Export list data into tabular format
  */


class JobReport(jl: JobList) {

  def exportStatistics() {

    jl.requireListFile()

    val statsFile = File(jl.file + ".runinfo.log")

    statsFile.write(Seq("job_id", "job_name", "queue", "submit_time", "start_time", "finish_time",
      "exec_host", "status", "user", "resubmission_of").mkString("\t"))
    statsFile.appendNewLine()


    val allJobs = List.concat(jl.jobs, jl.resubGraph().keys)

    val (infoJobs, misInfoJobs) = allJobs.partition(_.infoFile.isRegularFile)

    if (misInfoJobs.nonEmpty) {
      Console.err.println(
        "The following jobs were excluded because of missing run-information:" +
          misInfoJobs.map(_.id).mkString(",")
      )
    }

    infoJobs.map(_.info).
      map(ri => {
        Seq(
          ri.jobId, ri.jobName, ri.queue, ri.submitTime, ri.startTime, ri.finishTime,
          ri.execHost, ri.state, ri.user, Job(ri.jobId)(jl).resubOf.map(_.id).getOrElse("")
        ).mkString("\t")
      }).foreach(statsFile.appendLine)


    // also write congig header where possible
    val jcLogFile = File(jl.file + ".jobconf.log")
    jcLogFile.write(
      Seq("id", "name", "num_threads", "other_queue_args", "queue", "wall_time", "wd").mkString("\t")
    )
    jcLogFile.appendNewLine()


    //noinspection ConvertibleToMethodValue
    val allJC = allJobs.filter(_.isRestoreable).map(job => job -> job.config).toMap

    allJC.map({ case (job, jc) =>
      Seq(job.id, jc.name, jc.numThreads, jc.otherQueueArgs, jc.queue, jc.wallTime, jc.wd).mkString("\t")
    }).foreach(jcLogFile.appendLine)

    //      new {val runLog=statsFile; val configLog=jcLogFile}
  }


  def createHtmlReport() = {
    exportStatistics()
    println(s"${jl.file.name}: Exported statistics into ${jl.file.name}.{runinfo|jc}.log")

    Console.out.print(s"${jl.file.name}: Rendering HTML report...")

    val reportScript = scala.io.Source.fromURL(JobList.getClass.getResource("jl_report.R")).mkString

    val reportFile = scalautils.r.rendrSnippet(
      jl.file.name,
      reportScript, showCode = false,
      args = jl.file.pathAsString,
      wd = jl.file.parent
    )

    require(reportFile.isRegularFile, s"report generation failed for '$jl'")

    Console.out.println(s" done '${reportFile.name}'")
  }
}


//object ExportProps {
//  sealed trait ExportProps
//
//  case object basics extends ExportProps
//  case object logs extends ExportProps
//  case object runinfo extends ExportProps
//  case object qinfo extends ExportProps
//  case object all extends ExportProps
//
//  val allStates = Seq(basics, logs, runinfo, qinfo, all)
//
//  def valueOf(status: String) = allStates.find(_.toString == status) //.getOrElse(UNKNOWN)
//}

