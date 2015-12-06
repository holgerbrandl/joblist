package joblist

import better.files._
import joblist.shell.LocalScheduler
import org.scalatest.{BeforeAndAfter, FlatSpec, Matchers}


class LocalSchedulerTest extends FlatSpec with Matchers with BeforeAndAfter {

  //  import Matchers._; import joblist._

  val wd = (home / "unit_tests").createIfNotExists(true)

  // clean up old unit-test data before running each of the tests
  before {
    wd.list.foreach(_.delete(true))
  }


  it should "submit some multithreaded jobs of which some will fail, resubmit them" in {

    val jl = JobList(wd / ".unit_jobs", new LocalScheduler())
    jl.reset()
    jl.jobs

    val threadsPerJob = 4

    // submit some jobs
    val jobConfigs = for (fail_prob <- 1 to 100 by 5) yield {
      JobConfiguration(s"fake_job.sh 10 ${fail_prob}", numThreads = threadsPerJob)
    }

    jobConfigs.foreach(jl.run)

    Thread.sleep(3000)
    val expParJobs = jl.scheduler.asInstanceOf[LocalScheduler].NUM_THREADS / threadsPerJob
    jl.queueStatus.filter(_.status == "RUNNING") should have size expParJobs
  }
}


