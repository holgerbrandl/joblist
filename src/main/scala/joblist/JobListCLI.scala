package joblist

import java.io.{BufferedWriter, FileWriter}
import java.util.Objects

import better.files.File
import com.thoughtworks.xstream.XStream
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

  if (args.length < 2 || (args.length == 1 && (args(0) == "-h" || args(0) == "--help"))) {
    printUsageAndExit()
  }


  def printUsageAndExit(): Unit = {
    Console.err.println("""
Usage: jl <command> [options] [<joblist_file>]

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
      withExit(false). // just used for debugging
      parse(args.toList).
      map { case (key, value) => key -> Objects.toString(value) }.toMap
  }


  def submit() = {

    val results = parseArgs(args, """
Usage: jl submit [options] <joblist_file> <command>

Options:
 -n <threads>  Number of threads [default: 1]
 -q <queue>  Number of threads [default: short]
 -n <job_name>  Number of threads
 --other_queue_args <queue_args>  Additional queue parameters
    """)

    // todo add option to disable automatic stream redirection

    val jl = getJL(results)

    val jobName = results.get("<job_name>").get

    val jc = JobConfiguration(results.get("<command>").get, jobName, results.get("<queue>").get, results.get("<threads>").get.toInt, results.get("<other_queue_args>").get)


    // save for later in case we need to restore it

    val jobId = LsfUtils.bsub(jc)

    jl.add(jobId)


    // save task description in case we need to rerun it
    new XStream().toXML(jc, new BufferedWriter(new FileWriter(jobXml(jobId).toJava)))
  }


  def jobXml(jobId: Int): File = {
    File(s".jl/$jobId.job")
  }


  case class JobConfiguration(cmd: String, name: String, queue: String, numThreads: Int = 1, otherQueueArgs: String = "")


  def add() = {
    // val args = Array("jl", "add")
    val results = parseArgs(args, "Usage: jl add [options] [<joblist_file>]")

    val jl = getJL(results)

    try {

      // extract job id from stdin stream and add it to the given JobList

      val jobIds = io.Source.stdin.getLines().filter(_.startsWith("Job <")).map(_.split(" ")(1).replaceAll("[<>]", "").toInt).toList

      //      Console.err.println("pwd is "+File(".'"))
      Console.err.println(s"Adding job '${jobIds.mkString(", ")}' to joblist '${jl.file.toJava}'")

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
    val doc =
      """
    Usage: jl wait [options] <joblist_file>

    Options:
     --num_resubmits <num_resubmits>  The number of resubmission of jobs that hit the wall time limit of the underlying job scheduler [default: 0]
     --resubmit_strategy <resub_strategy>  The used escalation strategy for job resubmission [default: longer_queue]
      """.stripMargin

    val results = parseArgs(args, "Usage: jl add [options] <joblist_file>")

    val jl = getJL(results)
    jl.waitUntilDone()



    // in case jl.submit was used to launsch the jobs retry in case they've failed
    val maxResubmits = results.get("<num_resubmits>").get.toInt
    var numResubmits = 0


    while (numResubmits < maxResubmits && jl.killed.nonEmpty) {
      numResubmits = numResubmits + 1

      def isRestoreable(jobId: Int) = jobXml(jobId).isRegularFile
      val killedJobs = jl.killed

      require(killedJobs.forall(isRestoreable), "jobs can be resubmitted only if they were intially submitted with jl submit")

      // restore job configurations
      val taskConfigs: List[JobConfiguration] = killedJobs.map(jobId => new XStream().fromXML(jobXml(jobId).toJava).asInstanceOf[JobConfiguration])

      // use an independent job list for the resubmission
      val resubmitJL = JobList(File(jl.file.fullPath + s"_resubmit_$numResubmits"))
      taskConfigs.map(LsfUtils.bsub).map(resubmitJL.add)

      resubmitJL.waitUntilDone()
    }

    // decide escalation strategy -> more cores or longer queue, or just again?
  }


  def btop() = {
    val results = parseArgs(args, "Usage: jl up <joblist_file>")
    getJL(results).btop()
  }


  def kill() = {
    val results = parseArgs(args, "Usage: jl kill [options] <joblist_file>")
    getJL(results).kill()
  }


  def stats() = {
    val results = parseArgs(args, "Usage: jl kill [options] <joblist_file>")
    val jl = getJL(results)

    // todo dump some table or other format
  }


  def shortcuts() = {
    println("""
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
