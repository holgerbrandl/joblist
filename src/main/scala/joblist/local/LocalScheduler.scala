package joblist.local

import java.util.concurrent.{Executors, TimeUnit, _}

import better.files.File
import joblist._
import org.joda.time.DateTime

import scala.collection.mutable
import scala.util.Random
import scalautils.Bash
import scalautils.Bash.BashResult

/**
  * A multi-threaded implementation of a shell scheduler
  *
  * Adopted from https://www.richardnichols.net/2012/01/how-to-get-the-running-tasks-for-a-java-executor/
  *
  * @author Holger Brandl
  */
//noinspection TypeCheckCanBeMatch
class LocalScheduler extends JobScheduler {


  // from http://www.nurkiewicz.com/2014/11/executorservice-10-tips-and-tricks.html
  val NUM_THREADS = Runtime.getRuntime.availableProcessors() - 2

  private val jobstats = mutable.HashMap.empty[Int, RunInfo]
  private val dummies = mutable.HashMap.empty[Int, Seq[ThreadPlaceHolder]]


  // http://stackoverflow.com/questions/16161941/exectuorservice-vs-threadpoolexecutor-which-is-using-linkedblockingqueue
  //  val executorService = Executors.newFixedThreadPool(numCores)
  private val executor: ExecutorService = new ThreadPoolExecutor(NUM_THREADS, NUM_THREADS, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue[Runnable], Executors.defaultThreadFactory) {

    protected override def beforeExecute(t: Thread, r: Runnable) {
      if (r.isInstanceOf[JobRunnable]) {
        Thread.sleep(3000) // wait a second to avoid that jobs are actually started in add mode

        val jobId = r.asInstanceOf[JobRunnable].jobId
        jobstats += (jobId -> jobstats(jobId).copy(state = JobState.RUNNING, finishTime = new DateTime()))
      }
      super.beforeExecute(t, r)
    }


    protected override def afterExecute(r: Runnable, t: Throwable) {

      if (r.isInstanceOf[JobRunnable]) {

        val jobRunnable = r.asInstanceOf[JobRunnable]
        val jobId = jobRunnable.jobId

        val finalState = if (jobRunnable.hasFailed) JobState.FAILED else JobState.COMPLETED

        jobstats += (jobId -> jobstats(jobId).copy(state = finalState, finishTime = new DateTime()))

        // stop dummy threads
        dummies(jobId).foreach(_.asInstanceOf[ThreadPlaceHolder].shutdown = true)
        dummies.remove(jobId)
      }

      super.afterExecute(r, t)
    }
  }

  // see http://www.nurkiewicz.com/2014/11/executorservice-10-tips-and-tricks.html
  // https://twitter.github.io/scala_school/concurrency.html

  /** Submits a job and returns its jobID. */
  override def submit(jc: JobConfiguration): Int = {
    //    https://twitter.github.io/scala_school/concurrency.html

    require(NUM_THREADS >= jc.numThreads,
      s"""threading requirements of $jc can't be met be local scheduler which """ +
        s"allows at max for jobs using $NUM_THREADS threads")

    val jobId = new Random().nextInt(Int.MaxValue)

    val runInfo = new RunInfo(jobId, whoAmI, JobState.PENDING, jc.queue, "localhost", jc.name, new DateTime(), null, null, Int.MaxValue)
    jobstats += (jobId -> runInfo)

    // also create placeholder threads to respect thread settings
    // http://stackoverflow.com/questions/7530194/how-to-call-a-method-n-times-in-scala
    val threadPlacholders = (2 to jc.numThreads) map (x => new ThreadPlaceHolder())
    dummies += (jobId -> threadPlacholders)

    // schedule job and (optional) placeholders
    threadPlacholders.foreach(executor.execute(_))
    executor.execute(new JobRunnable(jc, jobId))

    jobId
  }

  case class JobRunnable(jc: JobConfiguration, jobId: Int) extends Runnable {
    var evalStatus: BashResult = null


    override def run(): Unit = {
      evalStatus = Bash.eval(jc.cmd, redirectStderr = jc.logs.err, redirectStdout = jc.logs.out, wd = jc.wd)
    }


    def hasFailed: Boolean = evalStatus.exitCode != 0
  }


  override def updateRunInfo(id: Int, runinfoFile: File): Unit = {
    toXml(jobstats(id), runinfoFile)
  }


  override def getQueued: List[QueueStatus] = {
    jobstats.
      filterNot({ case (id, runInfo) => JobState.finalStates.contains(runInfo.state) }).
      map { case (id, runInfo) => QueueStatus(id, runInfo.state.toString) }.toList
  }


  // not implemented because not applicable for local shell
  // todo throw more meaningful exception}
  override def readIdsFromStdin(): List[Int] = ???

  class ThreadPlaceHolder extends Runnable {

    // http://stackoverflow.com/questions/10630737/how-to-stop-a-thread-created-by-implementing-runnable-interface
    var shutdown = false


    override def run(): Unit = {
      while (!shutdown) {
        Thread.sleep(1000)
      }
    }
  }


  /** Cancel a list of jobs */
  override def cancel(jobIds: Seq[Int]): Unit = {
    // http://stackoverflow.com/questions/1562079/how-to-stop-the-execution-of-executor-threadpool-in-java
    executor.shutdownNow()
  }
}
