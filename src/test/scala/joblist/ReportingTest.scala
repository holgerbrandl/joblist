package joblist

import better.files._
import org.scalatest.{BeforeAndAfter, FlatSpec, Matchers}


class ReportingTest extends FlatSpec with Matchers with BeforeAndAfter {

  val wd = (home / "unit_tests").createIfNotExists(true)

  // clean up old unit-test data before running each of the tests
  before {
    wd.list.foreach(_.delete(true))
  }

  it should "create a report for a single job" in {
    //    import Matchers._; import joblist._
    val jl = new JobList(wd / ".single_job_report")
    jl.jobs

    jl.run(new JobConfiguration("sleep 1"))
    jl.waitUntilDone()

    jl.createHtmlReport()
  }

  it should "create a report when all jobs have failed" in {
    val jl = new JobList(wd / ".all_failed_report")

    jl.run(new JobConfiguration("exit 1"))
    jl.waitUntilDone()

    jl.createHtmlReport()
  }

  it should "include the complete resubmission history a report" in {
    val jl = new JobList(wd / ".resub_report")

    jl.createHtmlReport()
  }
}



