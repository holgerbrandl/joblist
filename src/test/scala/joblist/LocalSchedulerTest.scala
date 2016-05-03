package joblist

import better.files.File
import better.files.File._
import joblist.local.LocalScheduler
import org.scalatest.{BeforeAndAfter, FlatSpec, Matchers}


//noinspection TypeCheckCanBeMatch
class LocalSchedulerTest extends FlatSpec with Matchers with BeforeAndAfter {

  //  import Matchers._; import joblist._

  val wd = (home / "unit_tests").createIfNotExists(true)
  val jlHome = File(".")

  // clean up old unit-test data before running each of the tests
  before {
    wd.list.foreach(_.delete(true))
  }


  it should "submit some multi-threaded jobs " +
    "of which some are expected to fail, " +
    "adjust job settings and resubmit them, " +
    "and wait for completion" in {


    val jl = JobList(wd / ".unit_jobs", scheduler = new LocalScheduler)


    jl.reset()
    jl.jobs

    val threadsPerJob = 3


    // disable when running with travis-ci which just has one core
    if(jl.scheduler.asInstanceOf[LocalScheduler].NUM_THREADS < threadsPerJob){
      cancel
    }


    // submit some jobs
    val jobConfigs = for (fail_prob <- 1 to 100 by 5) yield {
      JobConfiguration(s"${jlHome}/test_data/fake_job.sh 10 ${fail_prob}", numThreads = threadsPerJob, wd = wd)
    }

    jobConfigs.foreach(jl.run)

    if (jl.scheduler.isInstanceOf[LocalScheduler]) {
      Thread.sleep(5000) // because local scheduling is delayed
      val expParJobs = jl.scheduler.asInstanceOf[LocalScheduler].NUM_THREADS / threadsPerJob
      jl.queueStatus().filter(_.state == JobState.RUNNING) should have size expParJobs
    }

    jl.waitUntilDone()
    jl.requiresRerun should not be empty
    jl.isDone should be(false)

    // tweak commands in resubmission so that they all make it
    jl.resubmit(new ResubmitStrategy {
      override def escalate(jc: JobConfiguration): JobConfiguration = {
        jc.copy(cmd = "sleep 1")
      }
    })

    jl.waitUntilDone()
    jl.requiresRerun shouldBe empty
    jl.jobs should have size 20
  }
}


