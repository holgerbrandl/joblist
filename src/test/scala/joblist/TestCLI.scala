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

  // note by default sbt is running tests in parallel which will fail like that
  before {
    Thread.sleep(1000)
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


  it should "submit a job and wait until it's done" in {
    val resultFile: File = wd / "hello_jl.txt"
    resultFile.delete(true)

    // http://stackoverflow.com/questions/7500081/scala-what-is-the-best-way-to-append-an-element-to-an-array
    val cmd: Array[String] = s"submit -j ${jl.file.pathAsString} -n test_job".split(" ") :+ s"sleep 2; touch ${resultFile.pathAsString}"
    JobListCLI.main(cmd)

    jl.file.toJava should exist
    //    bstatus should include(jl.jobIds.head.toString)

    // wait until its done
    JobListCLI.main(("wait " + jl.file.pathAsString).split(" "))
    jl.jobs.head.info

    resultFile.toJava should exist

    jl.jobs.head.isFinal should be(true)
  }


  it should "submit a job which is known to fail and resumit it once it fails" in {


    val failTagFile = File("dont_fail_job.txt")
    failTagFile.delete(true)
    File("no_fail.dat").delete(true)

    val bashCmd = s"""if [ ! -f "${failTagFile.pathAsString}" ]; then exit 1; fi; touch no_fail.dat"""
    val cmd: Array[String] = s"submit -j ${jl.file.pathAsString}".split(" ") :+ bashCmd
    JobListCLI.main(cmd)

    JobListCLI.main(("wait " + jl.file.pathAsString).split(" "))

    jl.file.toJava should exist
    jl.jobs.size should be(1)
    jl.killed should be(empty)
    jl.requiresRerun.size should be(1)

    // fix tag file to make job runnable and retry agin
    failTagFile.touch()

    Bash.eval(s"ls -l ${failTagFile.pathAsString}").exitCode should be(0)
    //    Bash.eval(bashCmd)
    //    System.gc()

    // wait with retry object to fix the failed one
    JobListCLI.main(("wait --resubmit_retry " + jl.file.pathAsString).split(" "))
    JobListCLI.main(("status " + jl.file.pathAsString).split(" "))

    //    jl.jobs.head.info
    //    jl.jobs.head.config.cmd
    jl.jobs.head.isDone should be(true)
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
      JobListCLI.main(("wait --resubmit_retry " + jl.file.pathAsString).split(" "))
    }

    jl.jobs.size should be(1)
    jl.failed.size should be(1)
    jl.requiresRerun.size should be(1)
  }
}
