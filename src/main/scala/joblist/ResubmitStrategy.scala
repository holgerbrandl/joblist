package joblist

/**
  * Document me!
  *
  * @author Holger Brandl
  */
trait ResubmitStrategy {

  /** Transform a job configuration into another one which is more likely to succeed. */
  def escalate(jc: JobConfiguration): JobConfiguration

}


class TryAgain extends ResubmitStrategy {

  override def escalate(jc: JobConfiguration): JobConfiguration = jc
}


class MoreThreads(threads: Int) extends ResubmitStrategy {

  override def escalate(jc: JobConfiguration): JobConfiguration = {
    require(threads > jc.numThreads, "Only an increased thread count is allowed for job escalation")
    jc.copy(numThreads = threads)
  }
}


class BetterQueue(queue: String) extends ResubmitStrategy {

  override def escalate(jc: JobConfiguration): JobConfiguration = jc.copy(queue = queue)
}

class DiffWalltime(wallTime: String) extends ResubmitStrategy {
  // todo ensure format is NN:NN

  override def escalate(jc: JobConfiguration): JobConfiguration = jc.copy(wallTime = wallTime)
}


class CompoundStrategy(elements: ResubmitStrategy*) extends ResubmitStrategy {
  override def escalate(jc: JobConfiguration): JobConfiguration = {
    elements.foldLeft(jc)((jc, rs) => rs.escalate(jc))
  }
}