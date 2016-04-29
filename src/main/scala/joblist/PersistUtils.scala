package joblist

import java.io.{BufferedWriter, FileWriter}

import better.files.File
import com.thoughtworks.xstream.XStream
import com.thoughtworks.xstream.converters.basic.AbstractSingleValueConverter
import com.thoughtworks.xstream.io.xml.DomDriver
import joblist.JobState.JobState
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

/**
  * Utilities to persist job state info
  */
object PersistUtils {


  //noinspection AccessorLikeMethodIsUnit
  def toXml(something: Any, file: File) = {
    getXstream.toXML(something, new BufferedWriter(new FileWriter(file.toJava)))
  }


  def fromXml(file: File) = {
    getXstream.fromXML(file.toJava)
  }


  // see http://x-stream.github.io/converter-tutorial.html
  private class BetterFilerConverter extends AbstractSingleValueConverter {

    def canConvert(o: Class[_]): Boolean = {
      o == classOf[File]
    }


    def fromString(str: String): AnyRef = {
      File(str)
    }
  }


  // see http://x-stream.github.io/converter-tutorial.html
  private class JodaConverter extends AbstractSingleValueConverter {

    def canConvert(o: Class[_]): Boolean = {
      o == classOf[DateTime]
    }


    val formatter = DateTimeFormat.forPattern("dd-MM-yyyy HH:mm:ss")


    override def toString(obj: scala.Any): String = formatter.print(obj.asInstanceOf[DateTime])


    def fromString(str: String): AnyRef = {
      formatter.parseDateTime(str)
      //      new DateTime(new Date(str))
    }
  }

  // see http://x-stream.github.io/converter-tutorial.html
  private class JobStateConverter extends AbstractSingleValueConverter {

    def canConvert(o: Class[_]): Boolean = {
      o.getName.startsWith("joblist.JobState$")
    }


    override def toString(obj: scala.Any): String = {
      obj.asInstanceOf[JobState].toString
    }


    def fromString(str: String): AnyRef = {
      JobState.valueOf(str)
    }
  }


  def getXstream: XStream = {
    val xStream = new XStream(new DomDriver())

    xStream.registerConverter(new BetterFilerConverter())
    xStream.registerConverter(new JodaConverter())
    xStream.registerConverter(new JobStateConverter())

    xStream.alias("RunInfo", classOf[RunInfo])
    xStream.alias("JobState", classOf[JobState])
    xStream.alias("JobConfig", classOf[JobConfiguration])

    //    xStream.alias("state", classOf[RunInfo], null)
    //    xStream.alias("state", classOf[JobState], null)

    // http://stackoverflow.com/questions/2008043/xstream-removing-class-attribute
    xStream.aliasSystemAttribute(null, "class")

    //    does not work because of http://stackoverflow.com/questions/14079859/xstream-annotation-not-working-in-scala
    //    xStream.autodetectAnnotations(true)
    //    xStream.processAnnotations(classOf[JobState])
    //    xStream.processAnnotations(classOf[JobConfiguration])
    //    xStream.processAnnotations(classOf[RunInfo])

    xStream
  }
}
