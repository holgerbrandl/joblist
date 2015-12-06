package joblist.misc

import java.nio.file.Files

import better.files.File


/**
  * Created by brandl on 11/24/15.
  */
object JlDebug extends App {

  import joblist._

  //  implicit val jl = new JobList("/Volumes/projects/plantx/inprogress/stowers/dd_Pgra_v4/bac_contamination/.blastn")
  implicit val jl = new JobList()
  jl.reset()
  jl.status
  jl.run(JobConfiguration("sleep 10", "test_job", numThreads = 3))

  jl.waitUntilDone()

  jl.status



  //  jl.waitUntilDone()
  //  jl.resubmit(jl.failed, new TryAgain)
  //  jl.exportStatistics(File(jl.file.fullPath + ".stats"))

  //  jl.waitUntilDone()

}

class FileTests {

  import joblist._

  val aFile = File("/lustre/projects/plantx/inprogress/test.txt")
  for (a <- 1 to 1000) {
    aFile.lines
    //    Files.readAllLines(someFile.path)
    aFile.write(a + "")
  }

  val someFile = File("test.txt")
  someFile.write("tt")

  for (a <- 1 to 10000) {
    someFile.allLines
    Files.readAllLines(someFile.path)
  }


  System.gc()
  System.gc()

  // single file
  Files.lines(someFile.path).iterator()
  // cause of all evil
  // http://www.rationaljava.com/2015/02/java-8-pitfall-beware-of-fileslines.html
  // https://bugs.openjdk.java.net/browse/JDK-8073923
  // http://howtodoinjava.com/2014/05/04/read-file-line-by-line-in-java-8-streams-of-lines-example/
}
