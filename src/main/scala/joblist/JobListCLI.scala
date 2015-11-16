package joblist

import org.docopt.Docopt

import scala.collection.JavaConversions._

;


/**
  * A command line interface to the joblist API
  *
  * @author Holger Brandl
  */
object JobListCLI extends App {

  val version = 0.1


  if (args.length == 1 && (args(0) == "-v" || args(0) == "--version")) {
    println(s"""
    jl   v$version"
    Copyright 2015 Holger Brandl
    License   Simplified BSD
    https://github.com/holgerbrandl/joblist
    """)
    System.exit(0)
  }

  if (args.length == 0 && (args(0) == "-h" || args(0) == "--help")) {
    Console.err.println(s"""Usage: jl <command> [options] [joblist_file]
    Supported commands are
      add   Allows to feeed stderr in jl which will extract the job-id and add it to the list
      wait  Wait for a list of tasks to finish
      up    Moves a list of jobs to the top of a queue (if supported by the used queuing system

    By default joblists are trimmed to the right margin of th e
      """)

    System.exit(0)
  }


  val argsNoTask = args.toList


  args(0) match {
    case "add" => add(argsNoTask)
    case "wait" => wait4jl(argsNoTask)
  }


  def add(argsNoTask: List[String]) = {
    //    def args() = "--chunk_size 5 /home/brandl/Dropbox/cluster_sync/scalautils/src/test/resources/bio/some_seqs_first_20.fasta".split(" ").toList
    val doc =
      """
    Usage: jl add [options] [joblist_file]

    Options:
     --chunk_size <chunk_size>  The number of sequences per chunk [default: 400]
      """.stripMargin

    val results = new Docopt(doc).parse(argsNoTask).map { case (key, value) => key -> value.toString }

  }


  def wait4jl(argsNoTask: List[String]) = {}


  def toTop(argsNoTask: List[String]) = {}


  def stats(argsNoTask: List[String]) = {}


  // todo continue

}
