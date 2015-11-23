package joblist

import java.util.Objects

import better.files.File
import org.docopt.Docopt

import scala.collection.JavaConversions._
import scala.collection.mutable
import scalautils.ShellUtils
import scalautils.StringUtils._


/**
  * A command line interface to the joblist API
  *
  * @author Holger Brandl
  */
object JobListCLI extends App {

  val version = 0.2

  val DEFAULT_JL = ".jobs"

  //  Console.err.println(s"args are ${args.mkString(", ")}")

  if (args.length == 1 && (args(0) == "-v" || args(0) == "--version")) {
    println(
      s"""
    JobList     v$version
    Copyright   2015 Holger Brandl
    License     Simplified BSD
    https://github.com/holgerbrandl/joblist
    """.alignLeft.trim)
    System.exit(0)
  }

  if (args.length < 1 || (args.length == 1 && (args(0) == "-h" || args(0) == "--help"))) {
    printUsageAndExit()
  }


  def printUsageAndExit(): Unit = {
    Console.err.println(
      """
      Usage: jl <command> [options] [<joblist_file>]

      Supported commands are
        submit    Submits a named job including automatic stream redirection and adds it to a joblist
        add       Allows to feeed stderr in jl which will extract the job-id and add it to the list
        wait      Wait for a list of tasks to finish
        up        Moves a list of jobs to the top of a queue (if supported by the used queuing system
        shortcuts Print a list of bash helper function defiitions which can be added via eval  $(jl shortcuts)

      If no <joblist_file> is provided, jl will use '.jobs' as default
      """.alignLeft)

    System.exit(0)
  }


  def getJL(results: Map[String, String]) = {
    new JobList(File(Option(results("joblist_file")).getOrElse(DEFAULT_JL)))
  }


  args.head match {
    case "submit" => submit()
    case "add" => add()
    case "wait" => wait4jl()
    case "up" => btop()
    case "kill" => kill()
    case "chill" => ???
    case "shortcuts" => shortcuts()
    case _ => printUsageAndExit()
  }


  def parseArgs(args: Array[String], usage: String) = {
    new Docopt(usage).
      //      withExit(false). // just used for debugging
      parse(args.toList).
      map { case (key, value) =>
        key.stripPrefix("--").replaceAll("[<>]", "") -> Objects.toString(value)
      }.toMap
  }


  def submit() = {

    val results = parseArgs(args,
      """
    Usage: jl submit [options] <command>

    Options:
     -j --joblist_file <joblist_file> Joblist name [default: .jobs]
     -n --name <job_name>             Name of the job
     -t --num_threads <threads>       Number of threads [default: 1]
     -q --queue <queue>               Used queue [default: short]
     -O --other_queue_args <queue_args>  Additional queue parameters
      """.alignLeft.trim)

    // todo add option to disable automatic stream redirection

    val jl = getJL(results)

    val jc = JobConfiguration(
      cmd = results.get("command").get,
      name = results.getOrElse("name", ""),
      queue = results.get("queue").get,
      numThreads = results.get("num_threads").get.toInt,
      otherQueueArgs = results.getOrElse("other_queue_args", "")
    )

    // save for later in case we need to restore it
    jl.run(jc)
  }


  def add() = {
    // val args = Array("jl", "add")
    val results = parseArgs(args, "Usage: jl add [options] [<joblist_file>]")

    val jl = getJL(results)

    try {

      // extract job id from stdin stream and add it to the given JobList
      val jobIds = jl.scheduler.readIdsFromStdin()

      //      Console.err.println("pwd is "+File(".'"))

      require(jobIds.nonEmpty)
      jobIds.foreach(jl.add)

      //tbd adding just one job per invokation this is not strictly necessary
      // require(jobId.size==1, "just one job can be registered per invokation")
      //        jl.add(jobId.next)
      //        jl.add(jobId.next)

      jobIds
    } catch {
      case e: Throwable => throw new RuntimeException("could not extract jobid from stdin", e)
    }
  }


  def wait4jl() = {
    // val args = Array("jl", "wait")
    // val args = Array("jl", "wait", ".blastn")
    val doc =
      s"""
    Usage: jl wait [options] [<joblist_file>]

    Options:
     --resubmit_retry                 Simply retry without any change
     --resubmit_queue <resub_queue>   Resubmit to different queue
     --resubmit_wall <walltime>       Resubmit with different walltime limit
     --resubmit_threads <num_threads> Resubmit with more threads
     --email                          Send an email report to `` once this joblist has finished
     """.alignLeft.trim

    val results = parseArgs(args, doc)

    // wait until all jobs have finished
    val jl = getJL(results)
    jl.waitUntilDone()

    // in case jl.submit was used to launch the jobs retry in case they've failed
    // see http://docs.scala-lang.org/overviews/collections/concrete-mutable-collection-classes.html

    var numResubmits = 0
    val resubChain = extractResubStrats(results).toIndexedSeq

    while (jl.killed.nonEmpty && numResubmits < resubChain.size) {
      jl.resubmitKilled(resubChain.get(numResubmits))
      numResubmits = numResubmits + 1

      jl.waitUntilDone()
    }

    // reporting
    if (results.get("email").get.toBoolean) {
      ShellUtils.mailme(s"file.name has finished", s"status: ${jl.statusReport}")
      //todo include html report into email
    }
  }


  def extractResubStrats(results: Map[String, String]) = {
    val pargs = mutable.ListBuffer.empty[ResubmitStrategy]

    if (results.get("resubmit_retry").get.toBoolean) {
      pargs += new TryAgain()
    }

    val resubStrats = results.filter({ case (key, value) => value != "null" })

    if (resubStrats.contains("resubmit_queue")) {
      pargs += new BetterQueue(resubStrats.get("resubmit_queue").get)
    }

    if (resubStrats.contains("resubmit_wall")) {
      pargs += new DiffWalltime(resubStrats.get("resubmit_wall").get)
    }

    if (resubStrats.contains("resubmit_threads")) {
      pargs += new MoreThreads(results.get("resubmit_threads").get.toInt)
    }

    require(pargs.length < 2, "multiple resub strategies are not yet possible. See and vote for https://github.com/holgerbrandl/joblist/issues/4")

    pargs
  }

  def btop() = {
    val results = parseArgs(args, "Usage: jl up <joblist_file>")
    getJL(results).btop()
  }


  def kill() = {
    val results = parseArgs(args, "Usage: jl kill [options] [<joblist_file>]")
    getJL(results).kill()
  }


  def stats() = {
    val results = parseArgs(args, "Usage: jl kill [options] [<joblist_file>]")
    val jl = getJL(results)

    // todo dump some table or other format
  }


  def shortcuts() = {
    println(
      """
joblist(){
  jl add $*
}
export -f joblist

wait4jobs(){
  jl wait $*
}
export -f wait4jobs


##note: mysb replacement
jsub(){
  jl submit $*
}
      """)
  }

}
