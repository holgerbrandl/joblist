package joblist

import better.files._
import joblist.local.LocalScheduler
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

    val threadsPerJob = 3

    // submit some jobs
    val jobConfigs = for (fail_prob <- 1 to 100 by 5) yield {
      JobConfiguration(s"fake_job.sh 20 ${fail_prob}", numThreads = threadsPerJob)
    }

    jobConfigs.foreach(jl.run)

    Thread.sleep(3000)
    val expParJobs = jl.scheduler.asInstanceOf[LocalScheduler].NUM_THREADS / threadsPerJob
    jl.queueStatus.filter(_.status == "RUNNING") should have size expParJobs

    jl.waitUntilDone()

    jl.failed should not be empty

    // tweak commands in resubmission so that they all make it
    jl.resubmitFailed(new ResubmitStrategy {
      /** Transform a job configuration into another one which is more likely to succeed. */
      override def escalate(jc: JobConfiguration): JobConfiguration = {
        jc.copy(cmd = "sleep 1")
      }
    })

    jl.waitUntilDone()

    jl.failed shouldBe empty
    jl.jobs should have size (20)

  }
}


