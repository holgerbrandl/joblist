package joblist

import better.files.File._
import org.scalatest.{BeforeAndAfter, FlatSpec, Matchers}


class SlurmTests extends FlatSpec with Matchers with BeforeAndAfter {

  val wd = (home / "unit_tests").createIfNotExists(asDirectory = true)

  // clean up old unit-test data before running each of the tests
  before {
    wd.list.foreach(_.delete(true))
  }


  it should "report correct status if job exceeds memory configuration" in {
    if(!isSLURM) ignore


    fail("implement me!")

    //    import Matchers._; import joblist._
    val jl = new JobList(wd / ".single_job_report")
    jl.jobs

    jl.run(new JobConfiguration("sleep 1"))
    jl.waitUntilDone()

    jl.createHtmlReport()
  }
}



