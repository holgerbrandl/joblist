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
// note change to class to disable test
object TestTasks extends FlatSpec with Matchers {

  //  import Matchers._

  val wd = (home / "unit_tests").createIfNotExists(true)

  wd.list.map(_.delete())


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