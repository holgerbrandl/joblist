package joblist

import better.files.File

/**
  * Defines a job and how it should be run
  *
  * @author Holger Brandl
  */

case class JobConfiguration(cmd: String, name: String = "", wallTime: String = "", queue: String = "short", numThreads: Int = 1, otherQueueArgs: String = "", wd: File = File(".")) {

  def saveAsXml(jobId: Int, inDir: File) = {
    val xmlFile = JobConfiguration.jcXML(jobId, inDir)
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
}
