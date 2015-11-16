package joblist

import java.text.SimpleDateFormat
import java.util.Date

import better.files.File

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


  /** Submits a task to the LSF. */
  def bsub(cmd: String, name: Option[String] = None, joblist: JobList = new JobList(".jobs"), queue: String = "short", numCores: Int = 1, otherArgs: String = "", workingDirectory: File = File(".")) = {

    val threadArg = if (numCores > 1) s"-R span[hosts=1] -n $numCores" else ""
    val jobName = name.getOrElse(buildJobName(workingDirectory, cmd))
    val lsfArgs = s"""-q $queue $threadArg $otherArgs"""
    //    val job = s"""cd '${workingDirectory.fullPath}'; mysub "$jobName" '$cmd' $lsfArgs | joblist ${joblist.file.fullPath}"""

    //todo could be avoided if we would call bsub directly (because ProcessIO takes care that arguments are correctly provided as input arguments to binaries)
    require(!cmd.contains("'"))

    // create hidden log directory and log cmd as well as queuing args
    require(workingDirectory.isDirectory)

    val logsDir = workingDirectory / ".logs"
    logsDir.createIfNotExists(true)

    val jobLogs = JobLogs(name.get, joblist)

    val cmdLog = jobLogs.cmd
    require(cmdLog.notExists)
    cmdLog.write(cmd)

    jobLogs.queueArgs.write(lsfArgs)


    // submit the job to the lsf
    val bsubCmd = s"""
    cd '${workingDirectory.fullPath}'
    bsub  -J $jobName $lsfArgs '( $cmd ) 2>.logs/${jobLogs.err} 1>.logs/${jobLogs.out}'
    """

    val bsubStatus = Bash.eval(bsubCmd).stderr
    //    new BashSnippet(bsubCmd).inDir(workingDirectory).eval(new LocalShell)

    val jobSubConfirmation = bsubStatus.split("\n").filter(_.startsWith("Job <"))

    if (jobSubConfirmation.isEmpty)
      throw new RuntimeException(s"job submission of '${name.getOrElse(cmd)}' failed with:\n$bsubStatus")

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
    Seq(directory.parent.parent.name, directory.parent.name, Math.abs(cmd.hashCode).toString, timestamp).mkString("__")
  }

}
