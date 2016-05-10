package joblist

import better.files.File._
import org.scalatest.{BeforeAndAfter, FlatSpec, Matchers}

import scalautils.StringUtils.ImplStringUtils


class KillTests extends FlatSpec with Matchers with BeforeAndAfter {

  //  import Matchers._; import joblist._


  val wd = (home / "unit_tests").createIfNotExists(asDirectory = true)

  // clean up old unit-test data before running each of the tests
  before {
    wd.list.foreach(_.delete(true))
  }


  it should "detect memory execceding jobs as killed" in {
    if(isLocal) cancel
    if(isLSF) cancel // because memory limits do not seem to work properly

    val jl = new JobList(wd / ".mem_job")
    jl.reset()

    // from http://unix.stackexchange.com/questions/99334/how-to-fill-90-of-the-free-memory
    // more elegant yes | tr \\n x | head -c $BYTES |  cat  | { grep n || true; }
    // but process substituation does not seem to work with lsf

    val memHungryTask =
      """
      BYTES=$(echo $((1024*1024*1024*5))) # 5gb
      # BYTES=$(echo $((1024*1024*5))) # 5gb
      yes | tr \\n x | head -c $BYTES |  grep n
      sleep 30
      exit 0
      """.alignLeft


//    jl.run(new JobConfiguration(memHungryTask, wd= wd))
    jl.run(new JobConfiguration(memHungryTask, wd= wd, maxMemory=10))
    jl.waitUntilDone()
    jl.status

    jl.jobs.head.config
//    JobListCLI.shouldExit=false
//    JobListCLI.main(s"jl status --log err ${jl.file}".split(" "))

    jl.killed.size should be (1)
    jl.jobs.head.resubOf.isDefined shouldBe false

    // resubmit with 10gb limit
    jl.resubmit(new ResubmitStrategy {

      override def escalate(jc: JobConfiguration): JobConfiguration = jc.copy(maxMemory = 10000)
    })
    jl.waitUntilDone()
    jl.status
    jl.jobs.size should be (1)
    jl.isComplete shouldBe true

    // also validate resubmission model here
    jl.jobs.head.resubOf shouldBe defined
  }


  it should "detect user-cancelled jobs as such" in{
    if(isLocal) cancel

    val jl = new JobList(wd / ".user_cancel")
    jl.reset()

    jl.run(JobConfiguration("sleep 30"))

    // tbd: wait until the jobs actually starts to run; would it make a difference?
    Thread.sleep(10000) // 10sec

    jl.cancel()

    // this will just work if the status is updated beforehand
    jl.cancelled.size should be (1)
    jl.jobs.head.info

    // simply retry but be more patient this time
    jl.resubmit()
    jl.waitUntilDone()
    jl.cancelled shouldBe empty
    jl.isComplete shouldBe true
  }
}



