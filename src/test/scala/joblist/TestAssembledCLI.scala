package joblist

import better.files.File._
import better.files._
import org.scalatest.{BeforeAndAfter, FlatSpec, Matchers}

import scalautils.Bash
import scalautils.StringUtils.ImplStringUtils

/**
  * @author Holger Brandl
  */
class TestAssembledCLI extends FlatSpec with Matchers with BeforeAndAfter {

  //  import Matchers._; import joblist._

  val wd = (home / "unit_tests").createIfNotExists(asDirectory = true)
  //  val jlHome = File("/Users/brandl/Dropbox/cluster_sync/joblist")
  val jlHome = File(".")

  // clean up old unit-test data before running each of the tests
  before {
    wd.list.foreach(_.delete(true))
  }


  it should "submit a job, wait for it and create a report" in {
    val cmdSeq =
      s"""
    cd ${wd.pathAsString}
    jl submit "sleep 5"
    jl wait --report
    """.alignLeft


    Bash.eval(cmdSeq, showOutput = true)

    (wd / (DEFAULT_JL + ".html")).toJava should exist

    val jl = JobList(wd / DEFAULT_JL)
    jl.jobs.head.isFinal should be(true)
  }


  it should "use the shell launcher to trigger, monitor and resubmit jobs" in {
    val jl = JobList(wd / ".whit")

    val cmdSeq =
      s"""
    cd ${wd.pathAsString}
    jl submit -j ${jl.file.pathAsString} "echo foo"
    jl submit -j ${jl.file.pathAsString} "echo bar"
    jl submit -j ${jl.file.pathAsString} -O "-W 00:01" "sleep 120; touch whit.txt"
    jl wait
    jl resub --time "00:10" ${jl.file.pathAsString}
    """.alignLeft

    Bash.eval(cmdSeq, showOutput = true)

    jl.file.toJava should exist
    jl.jobs should have size 3
  }


  it should "run a scala script that is using jl-api and terminate" in {
    if(isTravisCI) cancel

    // this needs testing because if non-daemon threads were used by joblist jl, scripts won't terminate
    val resultFile: File = wd / "script_result.txt"
    resultFile.delete(true)


    val result = Bash.eval(
      s"""
         cd $wd
         $jlHome/test_data/quit_when_done.scala
      """.alignLeft, showOutput = true)

    result.exitCode should be(0)
    resultFile.toJava should exist
  }

  /** Cope with filesystem delay (e.g. on cluster-fs. */
  def waitForFile(file: File, maxTime: Int = 20): File = {
    var secCounter = maxTime

    while (secCounter > 0) {
      if (file.exists) return file

      secCounter = secCounter - 1
      println(s"waiting for $file")
      Thread.sleep(1000)
    }

    file
  }


  it should "split batch jobs up correctly" in {

    val script =
      s"""
    cd ${wd.pathAsString}

    jl reset
    cat "${jlHome}/test_data/test_stdin.txt" | jl submit --batch - --bsep '^##'

    jl wait
    """.alignLeft

    Bash.eval(script, showOutput = true)

    val jl = JobList(wd / DEFAULT_JL)

    jl.jobs should have size 3
    jl.failed should have size 0
  }



  // depends on https://github.com/holgerbrandl/joblist/issues/42
  //  ignore should "remember last jl in a robust manner " in {
  it should "remember last jl in a robust manner " in {

    Bash.eval("jl submit --jl .remtest 'echo test'", showOutput = true, wd=wd).exitCode should be(0)
    Bash.eval("jl wait", showOutput = true, wd=wd).exitCode should be(0)

    //  use remember me to fetch status
    Bash.eval("jl status", showOutput = true, wd=wd).stdout.find(_.contains("1 done")) shouldBe defined

    // after reset status should throw error
    Bash.eval("jl reset", showOutput = true, wd=wd).exitCode should be(0)
    Bash.eval("jl status", showOutput = true, wd=wd).exitCode should be(1)
  }
}
