package joblist

import better.files.File
import org.docopt.Docopt

import scala.collection.JavaConversions._


/**
  * A command line interface to the joblist API
  *
  * @author Holger Brandl
  */
object JobListCLI extends App {

  val version = 0.1

  val DEFAULT_JL = ".jobs"

  if (args.length == 1 && (args(0) == "-v" || args(0) == "--version")) {
    println(s"""
    jl   v$version"
    Copyright 2015 Holger Brandl
    License   Simplified BSD
    https://github.com/holgerbrandl/joblist
    """)
    System.exit(0)
  }

  if (args.length == 0 && (args.length == 1 && (args(0) == "-h" || args(0) == "--help"))) {
    printUsageAndExit()

  }


  def printUsageAndExit(): Unit = {
    Console.err.println("""Usage: jl <command> [options] <joblist_file>
    Supported commands are
      submit    Submits a named job including automatic stream redirection and adds it to a joblist
      add       Allows to feeed stderr in jl which will extract the job-id and add it to the list
      wait      Wait for a list of tasks to finish
      up        Moves a list of jobs to the top of a queue (if supported by the used queuing system
      shortcuts Print a list of bash helper function defiitions which can be added via eval  $(jl shortcuts)

    If no <joblist_file> is providd, jl will use '.jobs' as default
      """)

    System.exit(0)
  }


  def getJL(results: Map[String, String]) = {
    new JobList(File(Option(results("<joblist_file>")).getOrElse(DEFAULT_JL)))
  }


  val argList = args.toList



  argList.head match {
    //    case "submit" => submit(argList)
    case "add" => add(argList)
    case "wait" => wait4jl(argList)
    case "up" => btop(argList)
    case "kill" => kill(argList)
    case "chill" => ???
    case "shortcuts" => shortcuts(argList)
    case _ => printUsageAndExit()
  }


  def parseArgs(args: List[String], doc: String) = {
    new Docopt(doc).parse(args).map { case (key, value) => key -> value.toString }.toMap
  }


  def submit(argList: List[String]) = {
    val results = parseArgs(argList, "Usage: jl add [options] <joblist_file>")

  }


  def add(argList: List[String]) = {
    val results = parseArgs(argList, "Usage: jl add [options] <joblist_file>")

    val jl = getJL(results)

    try {
      val jobId = io.Source.stdin.getLines().next().split(" ")(2).replaceAll("<>", "").toInt
      jl.add(jobId)

      jobId
    } catch {
      case e: Throwable => throw new RuntimeException("could not extract jobid from stdin")
    }
  }


  def wait4jl(args: List[String]) = {
    val doc =
      """
    Usage: jl wait [options] <joblist_file>

    Options:
     --num_resubmits <num_submits>  The number of resubmission of jobs that hit the wall time limit of the underlying job scheduler [default: 3]
      """.stripMargin

    val results = parseArgs(args, "Usage: jl add [options] <joblist_file>")

    getJL(results).waitUntilDone()
  }


  def btop(args: List[String]) = {
    val results = parseArgs(args, "Usage: jl up <joblist_file>")
    getJL(results).btop()
  }

  def kill(args: List[String]) = {
    val results = parseArgs(args, "Usage: jl kill [options] <joblist_file>")
    getJL(results).kill()
  }


  def stats(args: List[String]) = {
    val results = parseArgs(args, "Usage: jl kill [options] <joblist_file>")
    val jl = getJL(results)

    // todo dump some table or other format
  }


  def shortcuts(args: List[String]) = {
    println("""
joblist(){
  jl add $*
}

wait4jobs(){
  jl wait $*
}


##note: mysb replacement
jsub(){
  jl submit $*
}
""")
  }

}
