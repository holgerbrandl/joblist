import java.io.{BufferedWriter, FileWriter}
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Date

import better.files.File
import com.thoughtworks.xstream.XStream
import com.thoughtworks.xstream.io.xml.StaxDriver
import joblist.lsf.LsfScheduler
import joblist.shell.ShellScheduler
import joblist.slurm.SlurmScheduler
import org.joda.time.DateTime

import scala.collection.JavaConversions._
import scalautils.Bash

/**
  * Some generic utilities used to manipulate, create and manage joblists.
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
                     jobName: String,
                     submitTime: DateTime,
                     //                  ProjName:String, CpuUsed:Int, Mem:Int, Swap:Int, Pids:List[Int],
                     startTime: DateTime, finishTime: DateTime,
                     // additional fields
                     queueKilled: Boolean
                    ) {

    def isDone: Boolean = List("EXIT", "DONE").contains(status)


    def exceededWallLimit = status == "EXIT" // could also because it died
  }


  //noinspection AccessorLikeMethodIsUnit
  def toXml(something: Any, file: File) = {
    getXstrem.toXML(something, new BufferedWriter(new FileWriter(file.toJava)))
  }


  def fromXml(file: File) = {
    getXstrem.fromXML(file.toJava)
  }


  def getXstrem: XStream = {
    val xStream = new XStream(new StaxDriver())

    xStream.registerConverter(new BetterFilerConverter())
    xStream.registerConverter(new JodaConverter())
    xStream.alias("RunInfo", classOf[RunInfo])

    xStream
  }

  implicit class ImplFileUtils(file: File) {
    /** Workaround for https://github.com/pathikrit/better-files/issues/51 */
    def allLines = {
      Files.readAllLines(file.path).toList
    }
  }

}