package joblist.shell

import java.util.concurrent._

import better.files.File
import joblist.{JobConfiguration, JobScheduler, QueueStatus, RunInfo}

/**
  * A multi-threaded implementation of
  *
  * @author Holger Brandl
  */
class ShellScheduler extends JobScheduler {

  // tbd maybe we should just have this system-wide and not per instance

  // from http://www.nurkiewicz.com/2014/11/executorservice-10-tips-and-tricks.html
  val queue = new ArrayBlockingQueue(100)
  val numCores = Runtime.getRuntime.availableProcessors()

  //  val executorService = new ThreadPoolExecutor(numCores, numCores, 0L, TimeUnit.MILLISECONDS, queue)
  //  val executorService = Executors.newFixedThreadPool(numCores)

  // see http://www.nurkiewicz.com/2014/11/executorservice-10-tips-and-tricks.html
  // https://twitter.github.io/scala_school/concurrency.html
  /** Submits a job and returns its jobID. */
  override def submit(jc: JobConfiguration): Int = {
    //    https://twitter.github.io/scala_school/concurrency.html

    -1
  }


  val future = new FutureTask[String](new Callable[String]() {
    def call(): String = {
      "test"
    }
  })


  override def getRunInfo(runinfoFile: File): RunInfo = ???


  override def updateRunInfo(id: Int, runinfoFile: File): Unit = ???


  override def getQueued: List[QueueStatus] = {
    List()
  }


  override def readIdsFromStdin(): List[Int] = ???
}
