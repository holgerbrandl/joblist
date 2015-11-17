/**
  * Document me!
  *
  * @author Holger Brandl
  */ {

import java.io.File

import joblist.Tasks.BashSnippet
import joblist._


new File(".").getAbsoluteFile

val jobId = LsfUtils.bsub("""
echo test
echo test >&2
touch test_lsf.txt
""", "my_job")

  JobList().waitUntilDone()
val snippter = new BashSnippet("touch test_lsf.txt")
} {
  import joblist.Tasks.{BashSnippet, LsfExecutor}
  import joblist._

  val jobRunner = LsfExecutor(numThreads = 5, queue = "long", joblist = new JobList(".redoblastx"))

  BashSnippet("echo test").eval(jobRunner)
  BashSnippet("echo test2").eval(jobRunner)


  // block execution until are jobs are done
  jobRunner.joblist.waitUntilDone()

// get jobs that hit the Wall limit and resubmit them with more cores
//jobRunner.joblist.
//  killed.restore.
//  foreach(_.eval(jobRunner.copy(numThreads = 10)))
//}


//sumbit scala snippets


