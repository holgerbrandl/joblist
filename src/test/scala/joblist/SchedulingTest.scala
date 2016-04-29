package joblist

import better.files.File._
import joblist.local.LocalScheduler
import org.scalatest.{BeforeAndAfter, FlatSpec, Matchers}

import scalautils.CollectionUtils.StrictSetOps
import scalautils.StringUtils.ImplStringUtils

/**
  * Document me!
  *
  * @author Holger Brandl
  */
// note change to object to disable test
class SchedulingTest extends FlatSpec with Matchers with BeforeAndAfter {

  //  import Matchers._; import joblist._

  val wd = (home / "unit_tests").createIfNotExists(true)

  // clean up old unit-test data before running each of the tests
  before {
    wd.list.foreach(_.delete(true))
  }


  it should "submit a job, capture streams, wait for finish, and collect basic stats" in {

    val jl = JobList(wd / ".unit_jobs")
    jl.reset()
    jl.jobs


    val jobName = "testjob_" + System.currentTimeMillis()

    // we don't use multiline here to ease repl debugging with ammonite which still fails to process multiline strings
    val cmd = "sleep 3\necho \"hello stderr\" >&2\necho \"hello stdout\"\ntouch test_lsf.txt\n    "

    val job = jl.run(JobConfiguration(cmd, jobName))

    // check that serialize file is ther
    (wd / ".jl" / (s"${job.id}" + ".job")).toJava should exist

    jl.waitUntilDone()
    jl.jobs

    // did we fetch the correct runinfo
    jl.jobs.head.info.jobId should equal(job.id)

    //    val jobLogs = jl.logs.filter(_.name == jobName)
    //    (wd / s".logs/$jobName.cmd").toJava should exist

    //todo validate the other logs
  }


  it should "submit some jobs and wait until they are done " in {
    val jl = new JobList(wd / ".run_and_wait")
    //    val jl = JobList(wd / ".unit_jobs_test")

    val tasks = for (i <- 1 to 3) yield {
      JobConfiguration(s"""sleep 2; echo "this is task $i" > task_$i.txt """, wd = wd)
    }

    jl.run(tasks)

    // http://www.scalatest.org/user_guide/matchers_quick_reference

    // make sure that outputs have been created
    (wd / ".logs").toJava should exist

    jl.waitUntilDone()

    // relative globbing broken in b-f --> fixed for new version
    //    (wd / ".logs").glob("*").toList.head.lines.next should include ("medium")
    //    (wd / ".logs").list.filter(_.name.contains("args")).next().lines.next should include("medium")

    //    val wd = File("/Volumes/home/brandl/unit_tests")
    (wd / ".run_and_wait").toJava should exist


    (wd / "task_1.txt").toJava should exist
    (wd / "task_3.txt").toJava should exist

    (wd / "task_3.txt").lines.head shouldBe "this is task 3"

    // make sure that we can still access the job configurations
    val restoredJC = jl.jobs.map(_.config).head

    if (!jl.scheduler.isInstanceOf[LocalScheduler]) {
      restoredJC.queue should equal("medium")
    }
  }


  it should "resubmit killed jobs" in {
    // disable test if local scheduler is used
    // should no longer necessary once https://github.com/holgerbrandl/joblist/issues/17 has been fixed
    if (guessScheduler().isInstanceOf[LocalScheduler]) {
      cancel
    }

    val jl = JobList(wd / ".with_walllimit")

    val cmds = for (runMinutes <- 1 to 3) yield {
      s"""
        sleep ${60 * runMinutes - 30}
        touch walltime_test_${runMinutes}.txt
      """.alignLeft
    }

    jl.run(cmds.map(JobConfiguration(_, wallTime = "00:01", wd = wd)))

    jl.waitUntilDone()

    jl.jobs should have size (3)
    jl.killed should have size (2)
    jl.requiresRerun should have size (2)

    // resubmit killed jobs with more walltime
    jl.resubmit(new DiffWalltime("00:05"))
    jl.scheduler.getQueued should have size (2)

    jl.waitUntilDone()
    jl.killed should be('empty)
  }


  it should "not detect a failed job as being killed by the queuing system" in {

    val jl = JobList(wd / ".fail_no_walllimit")

    jl.run(JobConfiguration("""echo "other job""""))

    // run a job which failes
    jl.run(new JobConfiguration(
      """
    touch failjob_test.txt
    exit 1
      """, wd = wd))

    jl.waitUntilDone()

    jl.jobs.map(_.info)

    // check that is was partially run
    (wd / "failjob_test.txt").toJava should exist

    // ..not killed by the queue
    jl.killed should be(empty)

    // .. but is detected as failed
    jl.failed should have size (1)
  }

  it should "do a correct xor for QueueStatus" in {
    val someQS = List(QueueStatus(1, "RUN"), QueueStatus(2, "PEND"), QueueStatus(3, "RUN"))
    // one still running(1), one new(4), one change status and onne done(3)
    val otherQS = List(QueueStatus(1, "RUN"), QueueStatus(2, "RUN"), QueueStatus(4, "PEND"))

    val xor = someQS.strictXor(otherQS).map(_.jobId).distinct

    xor should not contain (1)
    xor should contain(2)
    xor should contain(3)
    xor should contain(4)
  }

  it should "reset should do a proper cleanup without harming non-jl files" in {

    (wd / "some_data.txt").touch()
    wd.list.size should be(1)

    val jl = JobList(wd / ".reset_test")
    jl.run(JobConfiguration("touch other_result.txt"))

    jl.waitUntilDone()

    jl.exportStatistics()

    jl.reset()

    // original + result + logsdir + 4logs

    wd.list.size should be(7)


  }


  //  implicit def bf2ioFile(bf: better.files.File): java.io.File = bf.toJava

  it should "submit jobs that contain single qutotes" in {
    // see https://github.com/holgerbrandl/joblist/issues/11

    wd.list.size should be(0)

    val resultFile = wd / "single_quote_result.txt"
    resultFile.delete(true)

    val jl = JobList(wd / ".single_quotes")
    jl.run(JobConfiguration(s"touch '${resultFile}'"))

    jl.waitUntilDone()


    resultFile.toJava should exist
    wd.list.size should be(3)
  }

}






