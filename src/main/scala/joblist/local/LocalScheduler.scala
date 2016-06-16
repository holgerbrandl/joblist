package joblist.local

import java.util.concurrent.{TimeUnit, _}

import better.files.File
import joblist.PersistUtils._
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
  val NUM_THREADS = {
    if (sys.env.get("JL_MAX_LOCAL_JOBS").isDefined)
      sys.env.get("JL_MAX_LOCAL_JOBS").get.toInt
    else
      Math.max(1, Runtime.getRuntime.availableProcessors() - 2)
  }

  private val jobstats = mutable.HashMap.empty[Int, RunInfo]
  private val dummies = mutable.HashMap.empty[Int, Seq[ThreadPlaceHolder]]


  // programs/scripts will only stop when there no longer is any non-daemon thread running
  // so will tag all of them as daemon to prevent the scheduler from prevent app-shutdown
  // http://stackoverflow.com/questions/1211657/how-to-shut-down-all-executors-when-quitting-an-application
  // http://stackoverflow.com/questions/7416018/when-does-the-main-thread-stop-in-java
  class DaemonThreadFactory extends ThreadFactory {
    override def newThread(r: Runnable): Thread = {
      val t = new Thread(r, classOf[LocalScheduler].getSimpleName + "-worker")
      t.setDaemon(true)
      t
    }
  }

  // http://stackoverflow.com/questions/16161941/exectuorservice-vs-threadpoolexecutor-which-is-using-linkedblockingqueue
  //  val executorService = Executors.newFixedThreadPool(numCores)
  private val executor: ExecutorService = new ThreadPoolExecutor(NUM_THREADS, NUM_THREADS, 0L, TimeUnit.MILLISECONDS,
    new LinkedBlockingQueue[Runnable], new DaemonThreadFactory()) {

    protected override def beforeExecute(t: Thread, r: Runnable) {
      if (r.isInstanceOf[JobRunnable]) {
        Thread.sleep(3000) // wait a second to avoid that jobs are actually started in add mode

        val jobId = r.asInstanceOf[JobRunnable].jobId
        jobstats += (jobId -> jobstats(jobId).copy(state = JobState.RUNNING, startTime = new DateTime()))
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

    // inform the users about ignored parameters
    def ignoreWarning(msg: String) = Console.err.println(this.getClass.getName + s": ${msg}")

    // see https://github.com/holgerbrandl/joblist/issues/17
    if (!jc.wallTime.isEmpty) ignoreWarning("ignoring walltime setting")
    if (jc.maxMemory > 0) ignoreWarning("ignoring memory setting")
    if (!jc.queue.isEmpty) ignoreWarning("ignoring queue setting")
    if (!jc.otherQueueArgs.isEmpty) ignoreWarning("ignoring other submission settings")


    if (jc.logs.err.exists) System.err.println(s"WARNING: job name '${jc.name}' is not unique. Existing stream-captures will be overridden or may be corrupted!")

    val jobId = new Random().nextInt(Int.MaxValue)

    val runInfo = new RunInfo(jobId, whoAmI, JobState.PENDING, "local", "none", "localhost", jc.name, new DateTime(), null, null, Int.MaxValue)
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


  override def updateRunInfo(jobId: Int, runinfoFile: File): Unit = {
    toXml(jobstats(jobId), runinfoFile)

    // remove final stats from fake queue
    //    dummies.remove(jobId)
  }


  override def getJobStates(jobIds: List[Int]): List[QueueStatus] = {
    jobstats.
      filterNot({ case (id, runInfo) => JobState.finalStates.contains(runInfo.state) }).
      map { case (id, runInfo) => QueueStatus(id, runInfo.state) }
        .toList
//        .filter(qs => jobIds.contains(qs.jobId)) // should no be necessary for local scheduler
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
    throw new RuntimeException("job killing is neither supported nor necessary when using the local schedueler")
    // http://stackoverflow.com/questions/1562079/how-to-stop-the-execution-of-executor-threadpool-in-java
    //    executor.shutdownNow()
  }
}
