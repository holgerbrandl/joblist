package joblist

import better.files.File._
import better.files._
import org.scalatest.{BeforeAndAfter, FlatSpec, Matchers}

import scalautils.Bash
import scalautils.IOUtils.BetterFileUtils.FileApiImplicits

/**
  * @author Holger Brandl
  */
class TestAssembledCLI extends FlatSpec with Matchers with BeforeAndAfter {

  //  import Matchers._; import joblist._

  val wd = (home / "unit_tests").createIfNotExists(true)

  // clean up old unit-test data before running each of the tests
  before {
    wd.list.foreach(_.delete(true))
  }


  it should "submit a job, wait for it and create a report" in {
    val cmdSeq = s"""
    cd ${wd.pathAsString}
    jl submit "sleep 5"
    jl wait --report
    """.alignLeft

    JobList().file.withExt(".html").toJava should exist
  }


  it should "use the shell launcher to trigger, monitor and resubmit jobs" in {
    val jl = JobList(wd / ".whit")

    val cmdSeq = s"""
    cd ${wd.pathAsString}
    jl submit -j ${jl.file.pathAsString} "echo foo"
    jl submit -j ${jl.file.pathAsString} "echo bar"
    jl submit -j ${jl.file.pathAsString} -O "-W 00:01" "sleep 120; touch whit.txt"
    jl wait --resubmit_wall "00:10" ${jl.file.pathAsString}
    """.alignLeft

    println(cmdSeq)

    Bash.eval(cmdSeq, showOutput = true)

    jl.file.toJava should exist
    jl.jobs should have size (3)
  }


  it should "run a scala script that is using jl-api and terminate" in {
    // this needs testing because if non-daemon threads were used by joblist jl, scripts won't terminate
    val result = Bash.eval("test_data/scripting/quit_when_done.scala", showOutput = true)

    result.exitCode should be(0)
    File("script_result.txt").toJava should exist
  }
}
