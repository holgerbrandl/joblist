package joblist.slurm

import better.files.File
import joblist._


/**
  * Document me!
  *
  * @author Holger Brandl
  */
class SlurmScheduler extends JobScheduler {

  override def getRunning: List[Int] = ???


  override def readRunLog(runinfoFile: File): RunInfo = ???


  override def updateRunInfo(id: Int, runinfoFile: File): Unit = ???


  override def submit(jc: JobConfiguration): Int = ???
}
