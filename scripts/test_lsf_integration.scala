

/**
  * Document me!
  *
  * @author Holger Brandl
  */ {

import java.io.File

import joblist.Tasks.BashSnippet
import joblist._
  import joblist.utils._


new File(".").getAbsoluteFile

  val jobId = guessQueue().submit("""
echo test
echo test >&2
touch test_lsf.txt
""", "my_job")

  JobList().waitUntilDone()
val snippter = new BashSnippet("touch test_lsf.txt")
}

val a = 1; {

  import joblist.Tasks.{BashSnippet, LsfExecutor}
  import joblist._

  val jobRunner = LsfExecutor(numThreads = 5, queue = "long", joblist = new JobList(".redoblastx"))

  BashSnippet("echo test").eval(jobRunner)
  BashSnippet("echo test2").eval(jobRunner)


  // block execution until are jobs are done
  val jl = jobRunner.joblist
  jl.waitUntilDone()

  // get jobs that hit the Wall limit and resubmit them with more cores
  val failedConfigs = jl.jobConfigs.filterKeys(!jl.jobIds.contains(_)).values

  jl.failedConfigs.map(_.copy(numThreads = 10)).foreach(jl.add())
  //}


  //sumbit scala snippets


}