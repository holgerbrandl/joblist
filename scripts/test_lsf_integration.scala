/**
  * Document me!
  *
  * @author Holger Brandl
  */


import java.io.File

import joblist.Tasks.BashSnippet
import joblist._


new File(".").getAbsoluteFile
val jobId = LsfUtils.bsub("""
echo test
echo test >&2
touch test_lsf.txt
""")

LsfUtils.wait4jobs()
val snippter = new BashSnippet("touch test_lsf.txt")
