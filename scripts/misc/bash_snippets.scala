/**
  * Document me!
  *
  * @author Holger Brandl
  */

/**
  * Document me!
  *
  * @author Holger Brandl
  */

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
