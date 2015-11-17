package joblist

import better.files._
import joblist.Tasks.{BashSnippet, LsfExecutor, StringOps}
import org.scalatest.{FlatSpec, Matchers}

import scalautils.Bash.BashMode

/**
  * Document me!
  *
  * @author Holger Brandl
  */
// note change to object to disable test
class TestTasks extends FlatSpec with Matchers {

  //  import Matchers._; import joblist._

  val wd = (home / "unit_tests").createIfNotExists(true)

  // clean up old unit-test data
  wd.list.foreach(_.delete())



  it should "submit a job, capture streams, wait for finish, and collect basic stats" in {

    // todo clean up directory

    val jl = JobList(wd / ".unit_jobs")
    jl.reset()

    jl.jobIds
    jl.logs


    val jobName = "testjob_" + System.currentTimeMillis()

    // we don't use multiline here to ease repl debugging with ammonite which still fails to process multiline strings
    val cmd = "sleep 3\necho \"hello stderr\" >&2\necho \"hello stdout\"\ntouch test_lsf.txt\n    "

    val jobId = LsfUtils.bsub(cmd, name = Some(jobName), workingDirectory = wd)
    jl.add(jobId)

    jl.waitUntilDone()
    jl.jobIds

    val jobLogs = jl.logs.filter(_.name == jobName)
    (wd / s".logs/$jobName.cmd").toJava should exist

    //todo validate the other logs
  }


  it should "submit some jobs and wait until they are done " in {
    val tasks = for (i <- 1 to 5) yield {
      BashSnippet(s"""sleep 15; echo "this is task $i" > task_$i.txt """).inDir(wd).withAutoName
    }

    val runner = new LsfExecutor(joblist = JobList(wd / ".test_tasks"), queue = "medium")
    runner.joblist.reset

    runner.eval(tasks)
    // tasks.foreach(_.eval(runner))
    // tasks.head.eval(runner)
    runner.joblist.waitUntilDone()

    // http://www.scalatest.org/user_guide/matchers_quick_reference

    // make sure that outputs have been created
    (wd / ".logs").toJava should exist

    // relative globbing broken in b-f --> fixed for new version
    //    (wd / ".logs").glob("*").toList.head.lines.next should include ("medium")
    (wd / ".logs").list.filter(_.name.contains("lsf")).next().lines.next should include("medium")

    //    val wd = File("/Volumes/home/brandl/unit_tests")
    (wd / ".test_tasks").toJava should exist


    (wd / "task_1.txt").toJava should exist
    (wd / "task_5.txt").toJava should exist

    (wd / "task_5.txt").lines.next shouldBe "this is task 5"
  }

  //  Bash.eval("echo test")(BashMode(beVerbose = true))
  //  Bash.eval("ls")
  //  BashSnippet("ls | grep ammo").inDir(home/"Desktop").eval(new LsfExecutor())
}


class playground extends App {

  BashSnippet(s"""sleep 60; echo "this is task 22" > task_22.txt """).inDir(root / "foo").inDir(home / "unit_tests").withAutoName

  val snippet = new BashSnippet("echo hello world $(pwd)").inDir(home / "Desktop")
  snippet.eval


  implicit val bashExecutor = LsfExecutor

  snippet.eval
  snippet.eval(new LsfExecutor)

  println("increasing verbosity")
  implicit val verboseBash = BashMode(beVerbose = true)
  snippet.withName("hello").eval(new LsfExecutor())

  "sdfsdf".toBash.eval

  implicit val jobRunner = LsfExecutor(queue = "short", joblist = JobList(home / "Desktop/"))
  "sleep 300".toBash.eval

  jobRunner.joblist.waitUntilDone()


  private val failedJobs = jobRunner.joblist
}


class lsf_test extends App {

}