package joblist.misc

/**
  * Created by brandl on 11/24/15.
  */
object JlDebug extends App {

  import better.files._
  import joblist._

  val jl = new JobList("/Volumes/projects/plantx/inprogress/stowers/dd_Pgra_v4/bac_contamination/.blastn.resubgraph")
  jl.exportStatistics(File(jl.file.fullPath + ".stats"))

}
