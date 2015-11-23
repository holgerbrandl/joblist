package joblist

import java.net.URI

import better.files.File
import com.thoughtworks.xstream.converters.basic.AbstractSingleValueConverter

/**
  * Document me!
  *
  * @author Holger Brandl
  */

case class JobConfiguration(cmd: String, name: String = "", queue: String = "short", numThreads: Int = 1, otherQueueArgs: String = "", wd: File = File(".")) {

  def saveAsXml(jobId: Int, inDir: File) = {
    val xmlFile = JobConfiguration.jcXML(jobId, inDir)
    toXml(this, xmlFile)
  }
}


// utility method for JC
object JobConfiguration {

  def jcXML(jobId: Int, inDir: File = File(".")): File = {
    inDir.createIfNotExists(true) / s"$jobId.job"
  }


  def fromXML(jobId: Int, wd: File = File(".")): JobConfiguration = {
    val xmlFile = jcXML(jobId, wd)
    fromXml(xmlFile).asInstanceOf[JobConfiguration]
  }
}


// see http://x-stream.github.io/converter-tutorial.html
class BetterFilerConverter extends AbstractSingleValueConverter {

  def canConvert(o: Class[_]): Boolean = {
    o == classOf[File]
  }


  def fromString(str: String): AnyRef = {
    File(new URI(str).toURL.getFile)
  }
}

