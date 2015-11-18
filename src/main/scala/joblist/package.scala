import java.text.SimpleDateFormat
import java.util.Date

import better.files.File
import joblist.lsf.LsfScheduler
import joblist.shell.ShellScheduler
import joblist.slurm.SlurmScheduler
import org.joda.time.DateTime

import scalautils.Bash

/**
  * Document me!
  *
  * @author Holger Brandl
  */
package object joblist {

  def guessQueue(): JobScheduler = {
    if (Bash.eval("which bsub").stdout.headOption.nonEmpty) {
      return new LsfScheduler()
    }

    if (Bash.eval("which squeue").stdout.headOption.nonEmpty) {
      return new SlurmScheduler()
    }

    new ShellScheduler
    //    throw new RuntimeException("Could not auto-detect queuing system. Are binaries in PATH?")
  }


  def buildJobName(directory: File, cmd: String) = {
    val timestamp = new SimpleDateFormat("MMddyyyyHHmmss").format(new Date())

    // todo this should also work when running in /

    Seq(directory.parent.parent.name, directory.parent.name, Math.abs(cmd.hashCode).toString, timestamp).mkString("__")
  }


  //  private def changeWdOptional(wd: File): String = {
  //    if (wd != null && wd != File(".")) "cd " + wd.fullPath + "; " else ""
  //  }
  case class RunInfo(jobId: Int, user: String, status: String, queue: String,
                     //                  FromHost:String,
                     execHost: String,
                     //                  JobName:String,
                     submitTime: DateTime,
                     //                  ProjName:String, CpuUsed:Int, Mem:Int, Swap:Int, Pids:List[Int],
                     startTime: DateTime, finishTime: DateTime) {

    def isDone: Boolean = List("EXIT", "DONE").contains(status)


    def exceededWallLimit = status == "EXIT" // could also because it died
  }


}