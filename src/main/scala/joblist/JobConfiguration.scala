package joblist

import java.io.{BufferedWriter, FileWriter}
import java.net.URI

import better.files.File
import com.thoughtworks.xstream.XStream
import com.thoughtworks.xstream.converters.basic.AbstractSingleValueConverter
import com.thoughtworks.xstream.io.xml.StaxDriver

/**
  * Document me!
  *
  * @author Holger Brandl
  */

case class JobConfiguration(cmd: String, name: String = "", queue: String = "short", numThreads: Int = 1, otherQueueArgs: String = "", wd: File = File(".")) {

  def saveAsXml(jobId: Int, inDir: File) = {
    val xmlFile = JobConfiguration.jcXML(jobId, inDir).toJava
    JobConfiguration.getXmlConverter.toXML(this, new BufferedWriter(new FileWriter(xmlFile)))
  }
}


// utility method for JC
object JobConfiguration {

  def jcXML(jobId: Int, inDir: File = File(".")): File = {
    inDir.createIfNotExists(true) / s"$jobId.job"
  }


  def fromXML(jobId: Int, wd: File = File(".")): JobConfiguration = {
    val xmlFile = jcXML(jobId, wd).toJava
    getXmlConverter.fromXML(xmlFile).asInstanceOf[JobConfiguration]
  }


  def getXmlConverter: XStream = {
    val xStream = new XStream(new StaxDriver())
    xStream.registerConverter(new BetterFilerConverter())

    xStream
  }
}


class BetterFilerConverter extends AbstractSingleValueConverter {

  def canConvert(o: Class[_]): Boolean = {
    o == classOf[File]
  }


  def fromString(str: String): AnyRef = {
    File(new URI(str).toURL.getFile)
  }
}

