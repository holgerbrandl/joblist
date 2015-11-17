package joblist

import java.text.SimpleDateFormat
import java.util.Date

import better.files.File
import joblist.JobListCLI.JobConfiguration
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

import scalautils.Bash

;


/**
  * A scala reimplemenation of  https://raw.githubusercontent.com/holgerbrandl/datautils/master/bash/lsf_utils.sh
  *
  * @author Holger Brandl
  */
object LsfUtils {



  def wait4jobs(joblist: JobList = new JobList(".jobs"), withReport: Boolean = false) = {
    joblist.waitUntilDone()

  }


  case class JobLogs(name: String, wd: File) {

    def logsDir = wd / s".logs"


    val idLog = logsDir / s"$name.jobid"


    def id: Int = {
      require(idLog.isRegularFile, "no id yet. job has not been submitted yet")

      idLog.lines.next().toInt
    }


    // file getters
    val cmd = logsDir / s"$name.cmd"
    val err = logsDir / s"$name.err.log"
    val out = logsDir / s"$name.out.log"
    val queueArgs = logsDir / s"$name.args"
  }


  /** Submits a task to the LSF. */
  // http://stackoverflow.com/questions/4652095/why-does-the-scala-compiler-disallow-overloaded-methods-with-default-arguments
  def bsub(task: JobConfiguration): Int = bsub(task.cmd, task.name, task.queue, task.numThreads, task.otherQueueArgs)


  def bsub(cmd: String, name: String, queue: String = "short", numCores: Int = 1, otherArgs: String = "", workingDirectory: File = File(".")): Int = {


    val threadArg = if (numCores > 1) s"-R span[hosts=1] -n $numCores" else ""
    val jobName = if (name == null || name.isEmpty) buildJobName(workingDirectory, cmd) else name
    val lsfArgs = s"""-q $queue $threadArg $otherArgs"""
    //    val job = s"""cd '${workingDirectory.fullPath}'; mysub "$jobName" '$cmd' $lsfArgs | joblist ${joblist.file.fullPath}"""

    //todo could be avoided if we would call bsub directly (because ProcessIO takes care that arguments are correctly provided as input arguments to binaries)
    require(!cmd.contains("'"))

    // create hidden log directory and log cmd as well as queuing args
    require(workingDirectory.isDirectory)

    val logsDir = workingDirectory / ".logs"
    logsDir.createIfNotExists(true)

    val jobLogs = JobLogs(jobName, workingDirectory)

    val cmdLog = jobLogs.cmd
    require(cmdLog.notExists)
    cmdLog.write(cmd)

    jobLogs.queueArgs.write(lsfArgs)


    // submit the job to the lsf
    val bsubCmd = s"""
    cd '${workingDirectory.fullPath}'
    bsub  -J $jobName $lsfArgs '( $cmd ) 2>${jobLogs.err.fullPath} 1>${jobLogs.out.fullPath}'
    """

    //    import sys.process._
    //    Seq("bsub",  "-J", jobName ,lsfArgs.split(" "), "(" +cmd +s") 2>.logs/${jobLogs.err} 1>.logs/${jobLogs.out}").flatten!!
    //    new BashSnippet(bsubCmd).inDir(workingDirectory).eval(new LocalShell)
    val bsubStatus = Bash.eval(bsubCmd).stdout


    val jobSubConfirmation = bsubStatus.filter(_.startsWith("Job <"))
    require(jobSubConfirmation.nonEmpty, s"job submission of '${jobName}' failed with:\n$bsubStatus")

    val jobID = jobSubConfirmation.head.split(" ")(1).drop(1).dropRight(1).toInt
    jobLogs.idLog.write(jobID.toString)

    jobID
  }


  private def changeWdOptional(wd: File): String = {
    if (wd != null && wd != File(".")) "cd " + wd.fullPath + "; " else ""
  }


  def mailme(subject: String, body: String = "", logSubject: Boolean = true) = {
    if (logSubject) Console.err.println(s"$subject")

    Bash.eval(s"""echo -e 'Subject:$subject\n\n $body' | sendmail $$(whoami)@mpi-cbg.de > /dev/null""")
  }


  def buildJobName(directory: File, cmd: String) = {
    val timestamp = new SimpleDateFormat("MMddyyyyHHmmss").format(new Date())

    // todo this should also work when running in /

    Seq(directory.parent.parent.name, directory.parent.name, Math.abs(cmd.hashCode).toString, timestamp).mkString("__")
  }


  // use abstract class here to support slurm as well
  case class RunLog(jobId: Int, user: String, status: String, queue: String,
                    //                  FromHost:String,
                    execHost: String,
                    //                  JobName:String,
                    submitTime: DateTime,
                    //                  ProjName:String, CpuUsed:Int, Mem:Int, Swap:Int, Pids:List[Int],
                    startTime: DateTime, finishTime: DateTime) {

    def isDone: Boolean = List("EXIT", "DONE").contains(status)


    def exceededWallLimit = status == "EXIT" // could also because it died
  }


  def updateRunInfo(jobId: Int, logFile: File) = {
    // todo write more structured/parse data here (json,xml) to ease later use

    val stats = Bash.eval(s"bjobs -lW ${jobId}").stdout
    // format is JobId,User,Stat,Queue,FromHost,ExecHost,JobName,SubmitTime,ProjName,CpuUsed,Mem,Swap,Pids,StartTime,FinishTime

    logFile.write(stats.mkString("\n"))
  }


  def readRunLog(logFile: File) = {
    val data = Seq(scala.io.Source.fromFile(logFile.toJava).
      getLines(). //map(_.replace("\"", "")).
      map(_.split("[ ]+").map(_.trim)).
      toSeq: _*)

    //digest from start and end
    val header = data(0).toList
    val values = data(1)


    val slimHeader = List(header.take(6), header.takeRight(8)).flatten
    val slimValues = List(values.take(6), values.takeRight(8)).flatten



    def parseDate(stringifiedDate: String): DateTime = {
      try {
        DateTimeFormat.forPattern("MM/dd-HH:mm:ss").parseDateTime(stringifiedDate).withYear(new DateTime().getYear)
      } catch {
        case _: Throwable => null
      }
    }

    RunLog(
      jobId = slimValues(0).toInt,
      user = slimValues(1),
      status = slimValues(2),
      queue = slimValues(3),
      execHost = slimValues(5),
      submitTime = parseDate(slimValues(6)),
      startTime = parseDate(slimValues(12)),
      finishTime = parseDate(slimValues(12))
    )
  }
}
