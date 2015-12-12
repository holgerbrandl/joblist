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

import joblist._


new File(".").getAbsoluteFile

val jobId = guessScheduler().submit(JobConfiguration("""
echo test
echo test >&2
touch test_lsf.txt
""", "my_job"))

JobList().waitUntilDone()
