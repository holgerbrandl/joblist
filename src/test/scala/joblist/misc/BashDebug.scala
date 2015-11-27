package joblist.misc

import better.files._
import joblist.misc.Tasks.BashSnippet

/**
  * Document me!
  *
  * @author Holger Brandl
  */
class BashDebug extends App {

  val wd = (home / "unit_tests").createIfNotExists(true)

  BashSnippet(s"""sleep 60; echo "this is task 22" > task_22.txt """).inDir(wd).inDir(wd).withAutoName

  val snippet = new BashSnippet("echo hello world $(pwd)").inDir(home / "Desktop")
  snippet.eval


  //  implicit val bashExecutor = LsfExecutor
  //
  //  snippet.eval
  //  snippet.eval(new LsfExecutor)
  //
  //  println("increasing verbosity")
  //  implicit val verboseBash = BashMode(beVerbose = true)
  //  snippet.withName("hello").eval(new LsfExecutor())
  //
  //  "sdfsdf".toBash.eval
  //
  //  implicit val jobRunner = LsfExecutor(queue = "short", joblist = JobList(wd))
  //  "sleep 300".toBash.eval
  //
  //  jobRunner.joblist.waitUntilDone()
  //
  //
  //  private val failedJobs = jobRunner.joblist
}
