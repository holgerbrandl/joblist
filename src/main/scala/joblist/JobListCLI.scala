package joblist

import better.files.File
import org.docopt.Docopt

import scala.collection.JavaConversions._
import scala.collection.mutable


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
      add       Allows to feeed stderr in jl which will extract the job-id and add it to the list
      wait      Wait for a list of tasks to finish
      up        Moves a list of jobs to the top of a queue (if supported by the used queuing system
      shortcuts Print a list of bash helper function defiitions which can be added via eval  $(jl shortcuts)

    By default joblists are trimmed to the right margin of th e
      """)

    System.exit(0)
  }


  val argList = args.toList


  argList.head match {
    case "add" => add(argList)
    case "wait" => wait4jl(argList)
    case "up" => ???
    case "kill" => ???
    case "chill" => ???
    case "shortcuts" => shortcuts(argList)
    case _ => printUsageAndExit()
  }


  def add(args: List[String]) = {
    //    def args() = "jl add .test_jobs".split(" ").toList
    val doc = "Usage: jl add [options] <joblist_file>"

    val results = new Docopt(doc).withExit(false).parse(args).map { case (key, value) => key -> value.toString }

    val jlFile = getJlFileFromOpts(results)

    try {
      val jobId = io.Source.stdin.getLines().next().split(" ")(2).replaceAll("<>", "").toInt
      jlFile.appendLine(jobId + "")
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

    val results = new Docopt(doc).parse(args).map { case (key, value) => key -> value.toString }

    val jlFile = getJlFileFromOpts(results)
    JobList(jlFile).waitUntilDone()
  }


  def toTop(args: List[String]) = {}


  def kill(args: List[String]) = {
    val doc =
      """Usage: jl kill [options] <joblist_file>

Options:
 --chunk_size <chunk_size>  The number of sequences per chunk [default: 400]
"""

    val results = new Docopt(doc).parse(args).map { case (key, value) => key -> value.toString }

  }


  def stats(args: List[String]) = {}


  def shortcuts(args: List[String]) = {
    println("""
joblist(){
  jl add $*
}

wait4jobs(){
  jl wait $*
}

wait4jobs(){
  jl wait $*
}
""")
  }


  def getJlFileFromOpts(results: mutable.Map[String, String]): File = {
    File(Option(results("<joblist_file>")).getOrElse(DEFAULT_JL))
  }
}
