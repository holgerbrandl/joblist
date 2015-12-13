package joblist

import java.util.Objects

import better.files.File
import joblist.local.LocalScheduler
import org.docopt.Docopt

import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scalautils.IOUtils.BetterFileUtils.FileApiImplicits
import scalautils.StringUtils._
import scalautils.{Bash, ShellUtils}


/**
  * A command line interface to the joblist API
  *
  * @author Holger Brandl
  */
object JobListCLI extends App {

  val version = "0.5-SNAPSHOT"

  val DEFAULT_JL = ".jobs"

  if (args.length == 1 && (args(0) == "-v" || args(0) == "--version")) {
    println(
      s"""
    JobList       v$version
    Description   An hpc-job manager
    Copyright     2015 Holger Brandl
    License       Simplified BSD
    Website       https://github.com/holgerbrandl/joblist
    """.alignLeft.trim)

    System.exit(0)
  }

  if (args.length < 1 || (args.length == 1 && (args(0) == "-h" || args(0) == "--help"))) {
    printUsageAndExit()
  }


  args.head match {
    case "submit" => submit()
    case "add" => add()
    case "wait" => wait4jl()
    case "status" => status()
    case "kill" => kill()
    case "up" => btop()
    case "shortcuts" => shortcuts()

    case _ => printUsageAndExit()
  }

  // don't quit but simply return when running the unit tests
  private val shouldExit = !Thread.currentThread().getName.contains("ScalaTest")

  if (shouldExit) {
    System.exit(0)
  }


  def printUsageAndExit(): Unit = {
    Console.out.println(
      """
      Usage: jl <command> [options] [<joblist_file>]

      Supported commands are
        submit    Submits a job to the underlying queuing system and adds it to the list
        add       Extracts job-ids from stdin and adds them to the list
        wait      Wait for a list of jobs to finish
        status    Prints various statistics and allows to create an html report for the list
        kill      Removes all  jobs of this list from the scheduler queue
        up        Moves a list of jobs to the top of a queue (if supported by the underlying scheduler)

      If no <joblist_file> is provided, jl will use '.jobs' as default
      """.alignLeft)

    //    shortcuts Print a list of bash helper function defiitions which can be added via eval  $(jl shortcuts)

    System.exit(0)
  }


  def getJL(options: Map[String, String], jlArgName: String = "joblist_file") = {
    val listFile = File(Option(options(jlArgName)).getOrElse(DEFAULT_JL))

    new JobList(listFile)
  }


  def parseArgs(args: Array[String], usage: String) = {
    new Docopt(usage).
      //      withExit(false). // just used for debugging
      parse(args.toList).
      map { case (key, value) =>
        key.stripPrefix("--").replaceAll("[<>]", "") -> {
          if (value == null) null else Objects.toString(value)
        }
      }.toMap
  }


  def submit(): Any = {

    val options = parseArgs(args,
      s"""
    Usage: jl submit [options] ( --batch <cmds_file> | <command> )

    Options:
     -j --jl <joblist_file>               Joblist name [default: .jobs]
     -n --name <job_name>                 Name of the job. Must be unique within this joblist
     -t --num_threads <threads>           Number of threads [default: 1]
     -q --queue <queue>                   Used queue [default: short]
     -O --other_queue_args <queue_args>   Additional queue parameters
     --debug                              Debug mode which will execute the first submission in the local shell and
                                          will ignore additional submissions. Creates a $${jl.name}.debug to track
                                          execution state
     --wait                               Wait until the job is done. Useful for single clusterized tasks
      """.alignLeft.trim)

    val jl = getJL(options, "jl")

    val baseConfig = JobConfiguration(null,
      queue = options.get("queue").get,
      numThreads = options.get("num_threads").get.toInt,
      otherQueueArgs = options.getOrElse("other_queue_args", "")
    )

    val jobConfigs = ListBuffer.empty[JobConfiguration]


    if (options.get("batch").get.toBoolean) {
      val batchArg = options.get("cmds_file").get

      // either read lines from file or from stdin if - is used
      val lines = if (batchArg == "-") {
        io.Source.stdin.getLines()
      } else {
        val batchFile = File(batchArg)
        require(batchFile.isRegularFile, s"batch file '${batchFile.name}' does not exist")

        batchFile.allLines
      }

      // convert all lines into jobs
      lines.map(batchCmd => {

        jobConfigs += baseConfig.copy(
          cmd = batchCmd,
          name = options.getOrElse("name", null)
        )
      })

    } else {
      jobConfigs += baseConfig.copy(
        cmd = options.get("command").get.alignLeft,
        name = options.getOrElse("name", "")
      )
    }


    // debug mode. Eval first submission in local shell and ignore subsequent ones until tag file is removed
    if (options.get("debug").get.toBoolean) {
      val jc = jobConfigs.head
      val debugTag = jl.file.withExt(".debug")

      if (!debugTag.exists) {
        debugTag.touch()

        Console.out.println(s"${jl.file.name}: eval head with command:\n${jc.cmd}")
        Bash.eval(jc.cmd, showOutput = true)
      } else {
        Console.out.println(s"${jl.file.name}: ignoring debug job submission. Remove ${debugTag.name} to debug again")
      }

      return
    }

    // save for later in case we need to restore it
    jobConfigs.foreach(jl.run)

    // we  block here in 2 situations
    // a) the user asked for it.
    // b) if a local scheduler is being used, which is suboptimal since the multithreading does not kick in
    if (options.get("wait").get.toBoolean) {
      jl.waitUntilDone()
    }
  }


  def add() = {
    val options = parseArgs(args, "Usage: jl add [options] [<joblist_file>]")

    val jl = getJL(options)

    try {
      // extract job id from stdin stream and add it to the given JobList
      val jobIds = jl.scheduler.readIdsFromStdin()

      require(jobIds.nonEmpty)
      jobIds.foreach(jl.add)

      jobIds
    } catch {
      case e: Throwable => throw new RuntimeException("could not extract jobid from stdin", e)
    }
  }


  def wait4jl() = {

    val doc =
      """
    Usage: jl wait [options] [<joblist_file>]

    Options:
     --resubmit_retry                   Simply retry without any change
     --resubmit_queue <resub_queue>     Resubmit to different queue
     --resubmit_wall <walltime>         Resubmit with different walltime limit
     --resubmit_threads <num_threads>   Resubmit with more threads
     --resubmit_type <fail_type>        Defines which failed jobs are beeing resubmitted. Possible values are all,
                                        killed or failed [default: all]
     --email                            Send an email report to the current user once this joblist has finished
     --report                           Create an html report for this joblist once the joblist has finished
     """.alignLeft.trim

    val options = parseArgs(args, doc)

    // wait until all jobs have finishedsl
    val jl = getJL(options)

    jl.requireListFile()

    restartLocalScheduler(jl)

    jl.waitUntilDone()

    // in case jl.submit was used to launch the jobs retry in case they've failed
    // see http://docs.scala-lang.org/overviews/collections/concrete-mutable-collection-classes.html

    var numResubmits = 0
    val resubChain = extractResubStrats(options).toIndexedSeq

    if (resubChain.nonEmpty) assert(jl.jobs.forall(_.isRestoreable), "all jobs must be submitted with jl to allow for resubmission")


    def tbd = options.getOrElse("fail_type", "all") match {
      case "all" => jl.requiresRerun
      case "failed" => jl.jobs.filterNot(_.wasKilled)
      case "killed" => jl.jobs.filter(_.wasKilled)
      case "canceled" => jl.jobs.filter(_.info.state == JobState.CANCELLED)
    }

    while (tbd.nonEmpty && numResubmits < resubChain.size) {
      // todo expose config root mapping as argument
      jl.resubmit(resubChain.get(numResubmits), getConfigRoots(tbd))

      numResubmits = numResubmits + 1

      jl.waitUntilDone()
    }

    // reporting
    if (options.get("email").get.toBoolean) {
      ShellUtils.mailme(s"${jl.file.name}: Processing Done ", s"""
      joblist: ${jl.toString}
      status: ${jl.status}
      """.alignLeft.trim)
      //todo include html report into email
    }

    if (options.get("report").get.toBoolean) {
      val reportFile = jl.createHtmlReport()
    }
  }


  /** Handle the local scheduler here: restart all non complete jobs */
  def restartLocalScheduler(jl: JobList): Unit = {
    if (jl.scheduler.isInstanceOf[LocalScheduler]) {
      jl.resubmit(resubJobs = jl.jobs.filterNot(_.isFinal))
    }
  }


  def extractResubStrats(options: Map[String, String]) = {
    val pargs = mutable.ListBuffer.empty[ResubmitStrategy]

    if (options.get("resubmit_retry").get.toBoolean) {
      pargs += new TryAgain()
    }

    val resubStrats = options.filter({ case (key, value) => value != "null" })

    if (resubStrats.get("resubmit_queue").get != null) {
      pargs += new BetterQueue(resubStrats.get("resubmit_queue").get)
    }

    if (resubStrats.get("resubmit_wall").get != null) {
      pargs += new DiffWalltime(resubStrats.get("resubmit_wall").get)
    }

    if (resubStrats.get("resubmit_threads").get != null) {
      pargs += new MoreThreads(options.get("resubmit_threads").get.toInt)
    }

    require(pargs.length < 2, "multiple resub strategies are not yet possible. See and vote for https://github.com/holgerbrandl/joblist/issues/4")

    pargs
  }


  def btop() = {
    val options = parseArgs(args, "Usage: jl up <joblist_file>")
    getJL(options).btop()
  }


  def kill() = {
    val options = parseArgs(args, "Usage: jl kill [options] [<joblist_file>]")
    getJL(options).kill()
  }


  def status(): Unit = {
    val options = parseArgs(args, """
    Usage: jl status [options] [<joblist_file>]

    Options:
     --report     Create an html report for this joblist
     --failed     Print ONLY ids of all final but not yet done jobs in this joblist. If empty the joblist
                  has been completely processed without errors. Useful for flow-control in bash scripts.
    """.alignLeft.trim)

    val jl = getJL(options)

    if (options.get("failed").get.toBoolean) {
      Console.out.print(jl.requiresRerun.map(_.id).mkString("\n"))
      return
    }

    println(jl.toString)

    // create an html report
    if (options.get("report").get.toBoolean) {
      val reportFile = jl.createHtmlReport()
      return
    }

    restartLocalScheduler(jl)
    println(jl.status)

    jl.jobs.map(_.info).
      map(ri => {
        Seq(ri.jobId, ri.jobName, ri.state).mkString("\t")
      }).
      foreach(println)
  }


  //  To use single verbs you can use some provided shortcuts by adding this to your bash_profile
  //  ```
  //  eval "$(jl shortcuts)"
  //  ```
  def shortcuts() = {
    println(
      """
      jladd(){
        jl add $*
      }
      export -f jladd

      jlwait(){
        jl wait $*
      }
      export -f wait4jobs

      ##note: mysb jlwait
      jlsub(){
        jl submit $*
      }
      export -f jlsub

      """.alignLeft)
  }
}
