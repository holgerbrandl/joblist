package joblist.misc

import java.nio.file.Files

import better.files.File


/**
  * Created by brandl on 11/24/15.
  */
object JlDebug extends App {

  import joblist._

  //  implicit val jl = new JobList("/home/brandl/test/.whit*")
  //  implicit val jl = new JobList("/Users/brandl/jl_test/.est_test")
  implicit val jl = new JobList(File(".myjobs"))
  jl.reset()
  jl.printStatus()

  for (jobNr <- 1 to 20) {
    jl.run(JobConfiguration("sleep 5", s"test_job_$jobNr"))
  }

  jl.printStatus()

  //  Thread.sleep(10000)
  println("waiting")


  //  jl.run(JobConfiguration("sleep 10", "test_job", numThreads = 3))
  jl.failed
  jl.isComplete
  jl.waitUntilDone()

  println("done")
  //  jl.showStatus()



  //  jl.waitUntilDone()
  //  jl.resubmit(jl.failed, new TryAgain)
  //  jl.exportStatistics(File(jl.file.pathAsString + ".stats"))

  //  jl.waitUntilDone()

  System.exit(0)
}

class FileTests {

  val aFile = File("/lustre/projects/plantx/inprogress/test.txt")
  for (a <- 1 to 1000) {
    aFile.lines
    //    Files.readAllLines(someFile.path)
    aFile.write(a + "")
  }

  val someFile = File("test.txt")
  someFile.write("tt")

  for (a <- 1 to 10000) {
    someFile.lines
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
