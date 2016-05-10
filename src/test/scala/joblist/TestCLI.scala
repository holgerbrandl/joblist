package joblist

import java.io.ByteArrayInputStream

import better.files.File._
import better.files._
import org.scalatest.{BeforeAndAfter, FlatSpec, Matchers}

import scala.language.postfixOps
import scalautils.Bash
import scalautils.Bash.eval

/**
  * Make sure that the CLI of joblist is working as expected by testing the provided subcommands
  *
  * @author Holger Brandl
  */
class TestCLI extends FlatSpec with Matchers with BeforeAndAfter {

  //  import Matchers._; import joblist._


  val wd = (home / "unit_tests").createIfNotExists(asDirectory = true)

  val jl = JobList(wd / ".cli_tests")
//  JobListCLI.shouldExit =false
  // note by default sbt is running tests in parallel which will fail like that
  before {
    wd.list.foreach(_.delete(true))
  }

  it should "capture the job id from stdin and wait for it" in {
    if (!isLSF) {
      cancel
    }

    val bstatus = eval("bsub 'touch cli_add_test.dat'").stdout.head

    // fake some stdin data which is normally provided by piping
    val in = new ByteArrayInputStream(bstatus.getBytes)
    System.setIn(in)

    // add it to jl
    JobListCLI.main(("add " + jl.file).split(" "))


    jl.file.toJava should exist
    bstatus should include(jl.jobs.head.id + "")

    // wait until its done
    JobListCLI.main(("wait " + jl.file).split(" "))

    jl.jobs.head.isFinal should be(true)
  }


  it should "submit a job which is known to fail and resubmit it once it fails" in {

    val failTagFile = wd / "dont_fail_job.txt"
    val noFailResult = wd / "no_fail.dat"

    val bashCmd = s"""if [ ! -f "${failTagFile.pathAsString}" ]; then exit 1; fi; touch ${noFailResult}"""
    val cmd = s"submit -j ${jl.file.pathAsString}".split(" ") :+ bashCmd
    JobListCLI.main(cmd)
    //    JobListCLI.shouldExit = false
    //    JobListCLI.main("status".split(" "))

//    JobListCLI.main("submit".split(" ") :+ "ls")

    JobListCLI.main(("wait " + jl.file.pathAsString).split(" "))

    jl.file.toJava should exist
    jl.jobs.size should be(1)
    jl.killed should be(empty)
    jl.requiresRerun.size should be(1)

    // fix tag file to make job runnable and retry agin
    failTagFile.toJava.createNewFile() // we can not use touch here because it does not work with sym-linked directories

    Bash.eval(s"ls -l ${failTagFile.pathAsString}").exitCode should be(0)
    //    Bash.eval(bashCmd)
    //    System.gc()

    // wait with retry object to fix the failed one
    JobListCLI.main(("wait " + jl.file.pathAsString).split(" "))
    JobListCLI.main(("resub --retry " + jl.file.pathAsString).split(" "))
    JobListCLI.main(("wait " + jl.file.pathAsString).split(" "))

    JobListCLI.main(("status " + jl.file.pathAsString).split(" "))

    //    jl.jobs.head.info
    //    jl.jobs.head.config.cmd
    jl.jobs.head.isCompleted should be(true)
  }


  it should "submit non-jl jobs but should not resubmit them" in {
    if (!isLSF) {
      cancel
    }

    val bstatus = eval(s"""bsub 'sleep 5; exit 1'""").stdout.head

    // fake some stdin data which is normally provided by piping
    val in = new ByteArrayInputStream(bstatus.getBytes)
    System.setIn(in)

    // addtry to run it
    JobListCLI.main(("add " + jl.file.pathAsString).split(" "))

    jl.jobs.size should be(1)

    JobListCLI.main(("wait " + jl.file.pathAsString).split(" "))

    jl.jobs.size should be(1)
    jl.failed.size should be(1)

    an[AssertionError] should be thrownBy {
      JobListCLI.main(("wait " + jl.file.pathAsString).split(" "))
      JobListCLI.main(("resub --retry " + jl.file.pathAsString).split(" "))
    }

    jl.jobs.size should be(1)
    jl.failed.size should be(1)
    jl.requiresRerun.size should be(1)
  }
}
