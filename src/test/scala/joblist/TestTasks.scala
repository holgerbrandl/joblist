package joblist

import better.files._
import joblist.Tasks.{BashSnippet, LsfExecutor}
import org.scalatest.{BeforeAndAfter, FlatSpec, Matchers}

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

    val jobId = LsfUtils.bsub(LsfJobConfiguration(cmd, jobName))
    jl.add(jobId)

    jl.waitUntilDone()
    jl.jobs

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

  //  Bash.eval("echo test")(BashMode(beVerbose = true))
  //  Bash.eval("ls")
  //  BashSnippet("ls | grep ammo").inDir(home/"Desktop").eval(new LsfExecutor())
}





