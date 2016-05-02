package joblist

/**
  * Resubmission patterns for jobs. They act as transformers on JobConfigurations which is simplified by the case-class
  * induced copy() operator.
  *
  * @author Holger Brandl
  */
trait ResubmitStrategy {

  /** Transform a job configuration into another one which is more likely to succeed. */
  def escalate(jc: JobConfiguration): JobConfiguration
}


class TryAgain extends ResubmitStrategy {

  override def escalate(jc: JobConfiguration): JobConfiguration = jc


  override def toString = "TryAgain"
}


case class MoreThreads(threads: Int) extends ResubmitStrategy {

  override def escalate(jc: JobConfiguration): JobConfiguration = {
    require(threads > jc.numThreads, "Only an increased thread count is allowed for job escalation")

    jc.copy(numThreads = threads)
  }
}


case class BetterQueue(queue: String) extends ResubmitStrategy {

  override def escalate(jc: JobConfiguration): JobConfiguration = jc.copy(queue = queue)
}


case class MoreTimeStrategy(wallTime: String) extends ResubmitStrategy {

  JobConfiguration.validateWallTime(wallTime)

  override def escalate(jc: JobConfiguration): JobConfiguration = jc.copy(wallTime = wallTime)
}


case class CompoundStrategy(elements: ResubmitStrategy*) extends ResubmitStrategy {

  override def escalate(jc: JobConfiguration): JobConfiguration = {
    elements.foldLeft(jc)((jc, rs) => rs.escalate(jc))
  }
}