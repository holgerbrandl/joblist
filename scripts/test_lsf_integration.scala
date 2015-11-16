/**
  * Document me!
  *
  * @author Holger Brandl
  */


import joblist.Tasks.BashSnippet
import joblist._



val jobId = LsfUtils.bsub("touch test_lsf.txt")

val snippter = new BashSnippet("touch test_lsf.txt")
