package joblist

import java.io.ByteArrayInputStream

import better.files._
import org.scalatest.{BeforeAndAfter, FlatSpec, Matchers}

import scala.language.postfixOps

/**
  * Make sure that the CLI of joblist is working as expected by testing the provided subcommands
  *
  * @author Holger Brandl
  */
class TestCLI extends FlatSpec with Matchers with BeforeAndAfter {


  val wd = (home / "unit_tests").createIfNotExists(true)

  val jl = JobList(wd / ".cli_tests")

  before {
    jl.reset()
  }

  it should "capture the job id from stdin and wait for it" in {
    import sys.process._

    val bstatus = "bsub 'touch cli_add_test.dat'" !!

    // fake some stdin data which is normally provided by piping
    val in = new ByteArrayInputStream(bstatus.getBytes)
    System.setIn(in)


    // add and...
    JobListCLI.main(("add " + jl.file.fullPath).split(" "))


    jl.file.toJava should exist
    bstatus should include(jl.jobIds.head.toString)

    // wait until its done
    JobListCLI.main(("wait " + jl.file.fullPath).split(" "))

    jl.jobs.head.info.isDone should be(true)
  }


  it should "submit a job and wait until it's done" in {
    val resultFile: File = wd / "hello_jl.txt"
    resultFile.delete(true)

    // http://stackoverflow.com/questions/7500081/scala-what-is-the-best-way-to-append-an-element-to-an-array
    val cmd: Array[String] = s"submit -j ${jl.file.fullPath} -n test_job".split(" ") :+ s"sleep 2; touch ${resultFile.fullPath}"
    JobListCLI.main(cmd)


    jl.file.toJava should exist
    //    bstatus should include(jl.jobIds.head.toString)

    // wait until its done
    JobListCLI.main(("wait " + jl.file.fullPath).split(" "))

    resultFile.toJava should exist

    jl.jobs.head.info.isDone should be(true)
  }


  //  it should "submit a job which is known to fail and resumit it once it fails" in {
  //    import sys.process._
  //
  //    val bstatus = "bsub 'touch cli_add_test.dat'" !!
  //
  //    // fake some stdin data which is normally provided by piping
  //    val in = new ByteArrayInputStream(bstatus.getBytes)
  //    System.setIn(in)
  //
  //
  //    // add and...
  //    JobListCLI.main(("add " + jl.file.fullPath).split(" "))
  //
  //
  //    jl.file.toJava should exist
  //    bstatus should include(jl.jobIds.head.toString)
  //
  //    // wait until its done
  //    JobListCLI.main(("wait " + jl.file.fullPath).split(" "))
  //
  //    jl.jobs.head.info.isDone should be(true)
  //  }
}
