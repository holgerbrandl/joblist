package joblist

import better.files.File
import joblist.JobConfiguration._
import joblist.PersistUtils._

import scala.collection.mutable.ListBuffer
import scala.util.Random

/**
  * Defines a job and how it should be run
  *
  * @author Holger Brandl
  */
case class JobConfiguration(cmd: String, name: String = "", wallTime: String = "", queue: String = "", numThreads: Int = 1, maxMemory: Int = 0,  otherQueueArgs: String = "", wd: File = File(".")) {

  // validate inputs
  if (!wallTime.isEmpty) validateWallTime(wallTime)
  require(maxMemory>=0, "memory limit must be greater than 0")
  require(numThreads>0, "thread limit must be greater than 0")


  def saveAsXml(jobId: Int, inDir: File) = {
    val xmlFile = jcXML(jobId, inDir)
    toXml(this, xmlFile)
  }


  /** If the job configuration does not come along with a name we create one is unique. */
  def withName() = {
    if (name == null || name.isEmpty) {
      this.copy(name = buildJobName(wd, cmd))
    } else {
      this
    }
  }


  // we don't use val here to avoid that it's serialized into the xml
  def logs = new JobLogs(name, wd)
}


/** Log files that might be of interest for the user. JL does not rely on them but tries to create in a consistent
  * manner them irrespective of the used scheduler. */
case class JobLogs(name: String, wd: File) {

  def logsDir = wd / s".logs"


  // file getters
  val err = logsDir / s"$name.err.log"
  val out = logsDir / s"$name.out.log"
  // disabled to reduce output clutter (see https://github.com/holgerbrandl/joblist/issues/43)
  //  val id = logsDir / s"$name.jobid"
  //  val cmd = logsDir / s"$name.cmd"

}

// companion object method for JC
object JobConfiguration {

  def jcXML(jobId: Int, inDir: File = File(".")): File = {
    inDir.createIfNotExists(true) / s"$jobId.job"
  }


  def fromXML(jobId: Int, wd: File = File(".")): JobConfiguration = {
    val xmlFile = jcXML(jobId, wd)
    fromXml(xmlFile).asInstanceOf[JobConfiguration]
  }


  def buildJobName(directory: File, cmd: String) = {
    var nameElements: ListBuffer[String] = ListBuffer()

    require(directory.isDirectory)

    def isDirNonRoot(f: File): Boolean = f.isDirectory && f.isDirectory && f.path.toString != "/"

    if (isDirNonRoot(directory.parent)) {
      nameElements += directory.parent.name
    }

    if (isDirNonRoot(directory)) {
      nameElements += directory.name
    }

    //    val timestamp = new SimpleDateFormat("MMddyyyyHHmmss").format(new Date())
    //    val timestamp = System.nanoTime().toString
    val timestamp = new Random().nextInt(Integer.MAX_VALUE).toString
    nameElements +=(Math.abs(cmd.hashCode).toString, timestamp)

    nameElements.mkString("__")
  }

  /** validate that the walltime format is [NN]N:NN */
  def validateWallTime(time: String): Unit = {
    // seehttps://github.com/holgerbrandl/joblist/issues/44
    require("[0-9]{1,3}:[0-9]{2}".r.pattern.matcher(time).matches)
  }
}
