package joblist

import java.lang.reflect.Modifier
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
  * A command line interface to the joblist API.
  *
  * @author Holger Brandl
  */
object JobListCLI extends App {

  val version = "0.7-SNAPSHOT"


  if (args.length == 1 && (args(0) == "-v" || args(0) == "--version")) {
    println(
      s"""
    JobList       v$version
    Description   An hpc-job manager
    Copyright     2016 Holger Brandl
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
    case "resub" => resubmit()
    case "status" => status()
    case "cancel" => cancel()
    case "up" => btop()
    case "shortcuts" => shortcuts()
    case "reset" => reset()

    case _ => printUsageAndExit()
  }

  // don't quit but simply return when running the unit tests
  var shouldExit = !Thread.currentThread().getName.contains("ScalaTest")

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
        resub     Resubmit non-complete jobs with escalated scheduler parameters
        status    Prints various statistics and allows to create an html report for the list
        cancel    Removes all  jobs of this list from the scheduler queue
        up        Moves a list of jobs to the top of a queue (if supported by the underlying scheduler)
        reset     Removes all information related to this joblist.

      If no <joblist_file> is provided, jl will use '.jobs' as default, but to save typing it will remember
      the last used joblist instance per directory.
      """.alignLeft)

    //    shortcuts Print a list of bash helper function definitions which can be added via eval  $(jl shortcuts)

    System.exit(0)
  }


  def getJL(options: Map[String, String], jlArgName: String = "joblist_file") = {
    //    val listFile = File(Option(options(jlArgName)).getOrElse(DEFAULT_JL))
    val listFile = File(Option(options(jlArgName)).getOrElse(getDefaultJlFile().absolute.toString()))

    val jl = new JobList(listFile)

    // keep track of last instantiated jl per directory (see https://github.com/holgerbrandl/joblist/issues/41)
    updateLastJL(jl)

    jl
  }


  def parseArgs(args: Array[String], usage: String) = {
    new Docopt(usage).
      //      withExit(false). // just used for debugging
      parse(args.toList).
      map { case (key, value) =>
        key
          .stripPrefix("--")
          .stripPrefix("-")
          .replaceAll("[<>]", "") -> {
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
     -t --threads <num_threads>           Number of threads [default: 1]
     -w --time <walltime>                 Maximal walltime formatted as '[hh]h:mm'
     -m --maxmem <maxmem>                 Maximal memory in Mb or using g-postfix for gigabytes (e.g. 5g)
     -q --queue <queue>                   Queue/partition to submit job to. Ignored for local scheduler
     -O --other_queue_args <queue_args>   Additional queue parameters
     --debug                              Debug mode which will execute the first submission in the local shell and
                                          will ignore additional submissions. Creates a $${jl.name}.debug to track
                                          execution state
     --wait                               Add the job and wait until it is done. Useful for single
                                          clusterized tasks
     --reset                              Reset the joblist prior to submission
     --bsep <separator_pattern>           Batch separator character to separate jobs [default: ^]
      """.alignLeft.trim)

    val jl = getJL(options, "jl")

    val waitForJob = options.get("wait").get.toBoolean
    val resetList = options.get("reset").get.toBoolean

    if (resetList) {
      jl.reset()
    }



    val baseConfig = JobConfiguration(null,
      wallTime = Option(options("time")).getOrElse(""),
      queue = Option(options("queue")).getOrElse(""),
      numThreads = options.get("threads").get.toInt,
      maxMemory = parseMemory(Option(options("maxmem")).getOrElse("0")),
      otherQueueArgs = Option(options("other_queue_args")).getOrElse(""))

    val jobConfigs = ListBuffer.empty[JobConfiguration]


    // for corresponding unit test see joblist/TestCLI.scala:113
    if (options.get("batch").get.toBoolean) {
      val batchArg = options.get("cmds_file").get

      // either read cmds from file or from stdin if - is used
      val cmds = if (batchArg == "-") {

        // fetch the command separator and split up stdin into into job commands
        val batchSep = options.get("bsep").get.r.unanchored

        // https://www.safaribooksonline.com/library/view/scala-cookbook/9781449340292/ch01s07.html
        // "es".r.unanchored.findFirstIn("tst").isDefined

        // io.Source.stdin.getLines() // old approach without customizable batch separation


        // http://stackoverflow.com/questions/7293617/split-up-a-list-at-each-element-satisfying-a-predicate-scala
        // http://stackoverflow.com/questions/14613995/whats-scalas-idiomatic-way-to-split-a-list-by-separator
        //        File("test_data/test_stdin.txt").lines.
//                File("/Users/brandl/Dropbox/cluster_sync/joblist/test_data/empty_chunks.txt").lines.
//                File("/Users/brandl/Dropbox/cluster_sync/joblist/test_data/non_regex_separators.txt").lines.
//                File("/Users/brandl/Dropbox/cluster_sync/joblist/test_data/alignment_test.txt").lines.
        io.Source.stdin.getLines().
          foldLeft(Seq(Seq.empty[String])) {
            (acc, line) =>
              if (batchSep.findFirstIn(line).isDefined) acc :+ Seq(line)
              else acc.init :+ (acc.last :+ line)
          }.map(_.mkString("\n"))

      } else {
        val batchFile = File(batchArg)
        require(batchFile.isRegularFile, s"batch file '${batchFile.name}' does not exist")

        batchFile.lines
      }


      // by doing so we allow to add more batch jobs to a batch jobs list (see https://github.com/holgerbrandl/joblist/issues/58)
      val batchBaseIndex = jl.jobs.size + 1

      // convert all cmds into jobs
      cmds.
        // remove empty batch jobs due to formatting of input
        filterNot(_.trim.isEmpty).
        map(_.alignLeft.trim).

        foreach(batchCmd => {

          jobConfigs += baseConfig.copy(
            cmd = batchCmd,
            name = Option(options("name")).getOrElse("jobs") + "__batch_" + (batchBaseIndex + jobConfigs.size)
          ) // todo unit-test batch submission
        })

    } else {
      jobConfigs += baseConfig.copy(
        cmd = options.get("command").get.alignLeft,
        name = Option(options("name")).getOrElse("")
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

    // submit all jobs to the queue
    jl.run(jobConfigs)

    // we  block here in 2 situations
    // a) the user asked for it.
    // b) if a local scheduler is being used, which is suboptimal since the multithreading does not kick in
    if (waitForJob) {
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
    //    val args = "wait".split(" ")
    val doc =
      """
    Usage: jl wait [options] [<joblist_file>]

    Options:
     --email                            Send an email report to the current user once this joblist has finished
     --report                           Create an html report for this joblist once the joblist has finished
      """.alignLeft.trim

    val options = parseArgs(args, doc)

    // wait until all jobs have finishedsl
    val jl = getJL(options)

    jl.requireListFile()

    restartLocalScheduler(jl)

    jl.waitUntilDone()


    // reporting
    if (options.get("email").get.toBoolean) {
      ShellUtils.mailme(s"${jl.file.name}: Processing Done ",
        s"""
      joblist: ${jl.toString}
      status: ${jl.status}
      """.alignLeft.trim)
      //todo include html report into email
    }

    if (options.get("report").get.toBoolean) {
      val reportFile = new JobReport(jl).createHtmlReport()
    }
  }


  def resubmit() = {
    //    val args = "resub".split(" ")
    val doc =
      """
    Usage: jl resub [options] [<joblist_file>]

    Options:
     --retry                              Simply retry without any change
     -t --threads <num_threads>           Resubmit with more threads
     -w --time <walltime>                 Resubmit with different walltime limit formatted as '[hh]h:mm'
     -m --maxmem <max_memory>             Maximal memory in Mb or using g-postfix for gigabytes (e.g. 5g)
     -q --queue <queue>                   Resubmit to different queue
     --type <fail_type>                   Defines which non-complete jobs should be resubmitted. Possible values
                                          are 'all', 'killed', 'cancelled' or 'failed' [default: all]
      """.alignLeft.trim

    val options = parseArgs(args, doc)

    val jl = getJL(options)
    jl.requireListFile()


    def tbd = options("type") match {
      case "all" => jl.requiresRerun
      case "failed" => jl.jobs.filterNot(_.wasKilled)
      case "killed" => jl.jobs.filter(_.wasKilled)
      case "cancelled" => jl.jobs.filter(_.info.state == JobState.CANCELLED)
    }

    assert(tbd.forall(_.isRestoreable), "all jobs must be submitted with jl to allow for resubmission")

    val resubStrategy = extractResubStrats(options).toIndexedSeq.headOption
    if (resubStrategy.isEmpty) {
      Console.err.println("Missing resubmission confifguration")
      println(doc)
      System.exit(1)
    }

    jl.resubmit(resubStrategy.get, getConfigRoots(tbd))
  }


  /** Handle the local scheduler here: restart all non complete jobs */
  def restartLocalScheduler(jl: JobList): Unit = {
    if (jl.scheduler.isInstanceOf[LocalScheduler]) {
      jl.resubmit(resubJobs = jl.jobs.filterNot(_.isFinal))
    }
  }


  def extractResubStrats(options: Map[String, String]) = {
    val pargs = mutable.ListBuffer.empty[ResubmitStrategy]

    if (options.get("retry").get.toBoolean) {
      pargs += new TryAgain()
    }

    val resubStrats = options.filter({ case (key, value) => value != "null" })

    if (resubStrats.get("queue").get != null) {
      pargs += new OtherQueue(resubStrats.get("queue").get)
    }

    if (resubStrats.get("time").get != null) {
      pargs += new MoreTime(resubStrats.get("time").get)
    }

    if (resubStrats.get("maxmem").get != null) {
      pargs += new MoreMemory(parseMemory(resubStrats.get("maxmem").get))
    }

    if (resubStrats.get("threads").get != null) {
      pargs += new MoreThreads(options.get("threads").get.toInt)
    }

    require(pargs.length < 2, "multiple resub strategies are not yet possible. See and vote for https://github.com/holgerbrandl/joblist/issues/4")

    pargs
  }


  private def parseMemory(memString:String) = memString.toLowerCase().replace("g", "000").toInt


  def btop() = {
    val options = parseArgs(args, "Usage: jl up <joblist_file>")
    getJL(options).btop()
  }


  def cancel() = {
    val options = parseArgs(args, "Usage: jl cancel [options] [<joblist_file>]")
    getJL(options).cancel()
  }


  def reset() = {
    val options = parseArgs(args, "Usage: jl reset [<joblist_file>]")
    getJL(options).reset()
  }


  def status(): Unit = {
    // val args = "status".split(" ")
    // val args = "status --verbose".split(" ")
    // val args = "status --verbose --killed ".split(" ")
    // val args = "status --verbose --killed --log cmd ".split(" ")
    // val args = "status --ids 1318504918,2117495693".split(" ")

    // not java-docopt is a bit limited here since it does not allow for empty lines between the options or differently name options sections
    // see https://github.com/docopt/docopt.java/issues/11

    val options = parseArgs(args,
      """
    Usage: jl status [options] [<joblist_file>]

    Options:
     --report             Create an html report for this joblist
     --failed             Only print the status for final but not yet done jobs in this joblist. This could be because those job
                          failed due to incorrect user logic, or because the queuing system killed them
                          has been completely processed without errors. Useful for flow-control in bash scripts.
     --killed             Only print the status of jobs killed by the queuing system.
     --ids <csv_ids>      Limit reporting to the comma-separted list of jobs ids
     --first              Limit reporting to the first job only
     --by_name <pattern>  Limit reporting to those jobs whose names match the given regex
     --no_header          Do not print the joblist summary as header
     --log <what>         Print details for selected jobs. Possible values are "cmd", "err", "out", "config" and "runinfo"
      """.alignLeft.trim)

    //    --fields <what>      Define which job details to include in the table. Comma-separated list of "basics","logs",
    //    "runinfo", "qinfo" or "basics" [default: all]


    val jl = getJL(options)

    jl.requireListFile()


    // create an html report
    if (options.get("report").get.toBoolean) {
      val reportFile = jl.createHtmlReport()
      return
    }

    restartLocalScheduler(jl)


    if (!options.get("no_header").get.toBoolean && !options.get("failed").get.toBoolean) {
      println(jl.toString)
      println(jl.status)
    }


    var statusJobs = jl.jobs

    if (options.get("failed").get.toBoolean) {
      statusJobs = jl.requiresRerun
    }

    if (options.get("killed").get.toBoolean) {
      statusJobs = jl.killed
    }


    if (options.get("ids").orNull != null) {
      val queryIds = options.get("ids").get.split(",")
      statusJobs = jl.jobs.filter(queryIds.contains(_))
    }

    if (options.get("first").get.toBoolean) {
      statusJobs = List(statusJobs.head)
    }

    // check what type of reporting we want to do (log or table)
    val logReporting = options.get("log").orNull

    if (logReporting != null) {

      statusJobs.foreach(job => {
        println("==> " + job.name + " <==")

        (logReporting match {
          case "err" => job.config.logs.err.lines
          case "out" => job.config.logs.out.lines
          //          case "cmd" => job.config.logs.cmd // simplified because of https://github.com/holgerbrandl/joblist/issues/43
          case "cmd" => job.config.cmd.split("\n").toList
          case "config" => JobConfiguration.jcXML(job.id, jl.dbDir).lines
          case "runinfo" => job.infoFile.lines
        }).foreach(println(_))

        println() // add an empty line for better readability
      })

      return
    }

    // by default do regular table reporting


    //    val isVerbose = options.get("verbose").get.toBoolean
    //    val fields = options.get("fields").get.split(",").map(ExportProps.valueOf(_))

    new JobReport(jl).exportStatistics()

    statusJobs.map(job => {
      val ri = job.info
      val fields = ListBuffer(ri.jobId, ri.jobName, ri.state)

      //      if (isVerbose) {
      //        //        fields += Seq(job.config.logs.err, job.config.logs.out)
      //        fields += File(".").relativize(job.config.logs.err)
      //        fields += File(".").relativize(job.config.logs.out)
      //        fields += File(".").relativize(job.config.logs.cmd)
      //      }

      fields.mkString("\t")

    }).foreach(println)
  }
}
