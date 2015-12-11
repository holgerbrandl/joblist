package joblist

import better.files._
import org.scalatest.{BeforeAndAfter, FlatSpec, Matchers}

import scalautils.Bash
import scalautils.IOUtils.BetterFileUtils.FileApiImplicits
import scalautils.StringUtils.ImplStringUtils

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
    cd ${wd.fullPath}
    jl submit "sleep 5"
    jl wait --report
    """.alignLeft

    JobList().file.withExt(".html").toJava should exist
  }


  it should "use the shell launcher to trigger, monitor and resubmit jobs" in {
    val jl = JobList(wd / ".whit")

    val cmdSeq = s"""
    cd ${wd.fullPath}
    jl submit -j ${jl.file.fullPath} "echo foo"
    jl submit -j ${jl.file.fullPath} "echo bar"
    jl submit -j ${jl.file.fullPath} -O "-W 00:01" "sleep 120; touch whit.txt"
    jl wait --resubmit_wall "00:10" ${jl.file.fullPath}
    """.alignLeft

    println(cmdSeq)

    Bash.eval(cmdSeq, showOutput = true)

    jl.file.toJava should exist
    jl.jobs should have size (3)
  }

}
