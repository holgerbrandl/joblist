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

    val jl = new JobList(wd / ".mem_job")
    jl.reset()

    // from http://unix.stackexchange.com/questions/99334/how-to-fill-90-of-the-free-memory
    val memHungryTask =
      """
      BYTES=$(echo $((1024*1024*1024*5))) # 1gb
      SECONDS=30
      cat <(yes | tr \\n x | head -c $BYTES) <(sleep $SECONDS) |  { grep n || true; }
      echo done filling memory
      exit 0
      """.alignLeft


//    jl.run(new JobConfiguration(memHungryTask, wd= wd))
    jl.run(new JobConfiguration(memHungryTask, wd= wd, maxMemory=10))
    jl.waitUntilDone()
    jl.status

    jl.killed.size should be (1)
    jl.jobs.head.resubOf.isDefined shouldBe false

    // resubmit with 10gb limit
    jl.resubmit(new ResubmitStrategy {

      override def escalate(jc: JobConfiguration): JobConfiguration = jc.copy(maxMemory = 10000)
    })
    jl.waitUntilDone()
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

    jl.cancelled.size should be (1)

    // simply retry but be more patient this time
    jl.resubmit()
    jl.waitUntilDone()
    jl.cancelled shouldBe empty
    jl.isComplete shouldBe true
  }
}



