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


    JobListCLI.main(("add " + jl.file.fullPath).split(" "))

    jl.file.toJava should exist
    bstatus should include(jl.jobIds.head.toString)

    jl.jobs.head.info.isDone should be(true)
  }
}
