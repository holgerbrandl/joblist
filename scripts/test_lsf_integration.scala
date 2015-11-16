/**
  * Document me!
  *
  * @author Holger Brandl
  */


import joblist.Tasks.BashSnippet
import joblist._


new BashSnippet("touch test_lsf.txt").eval

val jobId = LsfUtils.bsub("touch test_lsf.txt")
