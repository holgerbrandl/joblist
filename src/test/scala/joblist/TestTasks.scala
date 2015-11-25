package joblist

import better.files._
import joblist.Tasks.{BashSnippet, LsfExecutor}
import org.scalatest.{BeforeAndAfter, FlatSpec, Matchers}

import scalautils.Bash
import scalautils.StringUtils.ImplStringUtils

/**
  * Document me!
  *
  * @author Holger Brandl
  */
// note change to object to disable test
class TestTasks extends FlatSpec with Matchers with BeforeAndAfter {

  //  import Matchers._; import joblist._

  val wd = (home / "unit_tests").createIfNotExists(true)

  // clean up old unit-test data before running each of the tests
  before {
    wd.list.foreach(_.delete(true))
  }


  it should "submit a job, capture streams, wait for finish, and collect basic stats" in {

    // todo clean up directory

    val jl = JobList(wd / ".unit_jobs")
    jl.reset()
    jl.jobs


    val jobName = "testjob_" + System.currentTimeMillis()

    // we don't use multiline here to ease repl debugging with ammonite which still fails to process multiline strings
    val cmd = "sleep 3\necho \"hello stderr\" >&2\necho \"hello stdout\"\ntouch test_lsf.txt\n    "

    val jobId = jl.run(JobConfiguration(cmd, jobName))

    // check that serialize file is ther
    (wd / ".jl" / s"$jobId.job").toJava should exist

    jl.waitUntilDone()
    jl.jobs

    // did we fetch the correct runinfo
    jl.jobs.head.info.jobId should equal(jobId)
    jl.jobs.head.info.jobId should equal(jobId)

    //    val jobLogs = jl.logs.filter(_.name == jobName)
    //    (wd / s".logs/$jobName.cmd").toJava should exist

    //todo validate the other logs
  }


  it should "submit some jobs and wait until they are done " in {

    val tasks = for (i <- 1 to 3) yield {
      BashSnippet(s"""sleep 2; echo "this is task $i" > task_$i.txt """).inDir(wd).withAutoName
    }

    val runner = new LsfExecutor(joblist = JobList(wd / ".test_tasks"), queue = "medium")
    runner.joblist.reset()

    runner.eval(tasks)
    // tasks.foreach(_.eval(runner))
    // tasks.head.eval(runner)
    //    runner.joblist.waitUntilDone()

    // http://www.scalatest.org/user_guide/matchers_quick_reference

    // make sure that outputs have been created
    (wd / ".logs").toJava should exist

    // relative globbing broken in b-f --> fixed for new version
    //    (wd / ".logs").glob("*").toList.head.lines.next should include ("medium")
    //    (wd / ".logs").list.filter(_.name.contains("args")).next().lines.next should include("medium")

    //    val wd = File("/Volumes/home/brandl/unit_tests")
    (wd / ".test_tasks").toJava should exist


    (wd / "task_1.txt").toJava should exist
    (wd / "task_3.txt").toJava should exist

    (wd / "task_3.txt").lines.next shouldBe "this is task 3"

    // make sure that we can still access the job configurations
    val restoredJC = runner.joblist.jobConfigs.values.head
    restoredJC.queue should equal("medium")
  }


  it should "resubmit killed jobs" in {

    // todo clean up directory

    val jl = JobList(wd / ".with_walllimit")

    val cmds = for (runMinutes <- 1 to 3) yield {
      s"""
        sleep ${60 * runMinutes - 30}
        touch walltime_test_${runMinutes}.txt
      """.alignLeft
    }

    cmds.foreach(cmd => jl.run(JobConfiguration(cmd, wallTime = "00:01")))

    jl.waitUntilDone()

    jl.jobs should have size (3)
    jl.killed should have size (2)
    jl.failed should have size (2)

    // resubmit killed jobs with more walltime
    jl.resubmitKilled(new DiffWalltime("00:05"))

    jl.scheduler.getRunning should have size (2)

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
      """))

    jl.waitUntilDone()

    jl.jobs.map(_.info)

    // check that is was partially run
    (wd / "failjob_test.txt").toJava should exist

    // ..not killed by the queue
    jl.killed should be(Set.empty)

    // .. but is detected as failed
    jl.failed should have size (1)
  }


  it should "use the shell launcher to trigger, monitor and resubmit jobs" in {
    val jlName = ".whit"

    val jl = JobList(wd / jlName)

    Bash.eval(s"""
    cd $wd
    jl submit -j ${jlName} "echo foo"
    jl submit -j ${jlName} "echo bar"
    jl submit -j ${jlName} -O "-W 00:01" "sleep 120; touch whit.txt"
    jl wait --resubmit_wall "00:10" ${jlName}
    """.alignLeft)

    jl.file.toJava should exist
    jl.jobs should have size (3)
  }
}

class ReportingTest extends FlatSpec with Matchers {

  it should "take run log data and do some reporting" in {
    //    import Matchers._; import joblist._
    val jl = new JobList("test_data/reporting/.blastn")
    jl.jobs

    jl.exportStatistics()
  }
}





