package joblist

import java.util.concurrent.Executors

import better.files.File

import scala.concurrent.ExecutionContext
import scalautils.Bash


/**
  * Slim layer around bsub & friends
  *
  * @author Holger Brandl
  */
object Tasks extends App {


  abstract class SnippetExecutor {

    def eval(bashSnippet: BashSnippet): Unit


    def eval(tasks: Iterable[BashSnippet]) = {
      tasks.foreach(_.eval(this))
    }
  }


  class LocalShell extends SnippetExecutor {

    override def eval(bashSnippet: BashSnippet): Unit = {
      val status = Bash.eval(bashSnippet.cmd)
      println(status)
    }


    def eval(tasks: Iterable[BashSnippet], numThreads: Int = Runtime.getRuntime.availableProcessors()): Unit = {
      implicit val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(numThreads))

      tasks.par.foreach(_.eval(this))
    }
  }


  class DryRunExecutor extends SnippetExecutor {

    override def eval(bashSnippet: BashSnippet): Unit = {
      println(s"---running: ${bashSnippet.name} ---")
      println(bashSnippet.cmd)
      println("----------------")
    }
  }


  case class LsfExecutor(numThreads: Int = 1, queue: String = "short",
                         joblist: JobList = JobList(), wd: File = null) extends SnippetExecutor {

    // more r like default would be http://stackoverflow.com/questions/3090753/is-it-possible-for-an-optional-argument-value-to-depend-on-another-argument-in-s
    def wd_ = if (wd != null) wd else joblist.file.parent


    override def eval(tasks: Iterable[BashSnippet]): Unit = {
      super.eval(tasks)
      joblist.waitUntilDone()
    }


    override def eval(bashSnippet: BashSnippet): Unit = {
      // todo since we forward everything we could also inine bsub here
      val jobId = LsfUtils.bsub(bashSnippet.cmd, Some(bashSnippet.name), queue = queue, numCores = numThreads, joblist = joblist, workingDirectory = bashSnippet.wd)
      Console.err.println(s"Submitting '${bashSnippet.withAutoName.name}' to '$queue' with jobid '$jobId'")
    }
  }


  case class BashSnippet(cmd: String, name: String = "", wd: File = File(".")) {


    //http://stackoverflow.com/questions/7249396/how-to-clone-a-case-class-instance-and-change-just-one-field-in-scala
    def withName(name: String): BashSnippet = this.copy(name = name)


    def withAutoName: BashSnippet = {
      this.copy(name = LsfUtils.buildJobName(this.wd, this.cmd))
    }


    def inDir(wd: File) = {

      // trim first line if it's cd statement
      var splitCmd = this.cmd.split("\n")
      val wdPostFix = "## lsfutils:change into wd"
      if (splitCmd.head.contains(wdPostFix)) {
        splitCmd = splitCmd.drop(1)
      }
      val trimmedCmd = splitCmd.mkString("\n")

      this.copy(cmd = s"cd ${wd.path} $wdPostFix\n" + trimmedCmd, wd = wd)
    }


    def eval(implicit snippetEvaluator: SnippetExecutor = new DryRunExecutor()): Unit = {
      val namedSnippet = if (this.name.isEmpty) this.withAutoName else this
      snippetEvaluator.eval(namedSnippet)
    }
  }


  implicit class StringOps(str: String) {

    def toBash: BashSnippet = BashSnippet(str)
  }


}
