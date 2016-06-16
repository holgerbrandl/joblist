package joblist

import better.files.File
import better.files.File._
import org.scalatest.{BeforeAndAfter, FlatSpec, Matchers}
import scalautils.StringUtils.ImplStringUtils

import scalautils.Bash

/**
  * Created by brandl on 6/16/16.
  */
class TestBatchSubmission extends FlatSpec with Matchers with BeforeAndAfter {

  //  import Matchers._; import joblist._

  val wd = (home / "unit_tests").createIfNotExists(asDirectory = true)
  //  val jlHome = File("/Users/brandl/Dropbox/cluster_sync/joblist")
  val jlHome = File(".")

  // clean up old unit-test data before running each of the tests
  before {
    wd.list.foreach(_.delete(true))
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


  it should "left-align chunks and remove empty ones" in {

    val script =
      s"""
    cd ${wd.pathAsString}

    cat "${jlHome}/test_data/alignment_test.txt" | jl submit --batch - --bsep '## pilon'

    jl wait
    """.alignLeft

    Bash.eval(script, showOutput = true)

    val jl = JobList(wd / DEFAULT_JL)

    jl.jobs should have size 3
    jl.failed should have size 0

    // make sure that jobs have just 2 lines (ie are trimmed)
    val commandLines: Array[Delimiters] = jl.jobs.head.config.cmd.split("\n")
    commandLines should have size 2
    commandLines.drop(1).head should be ("echo test")
  }

}
