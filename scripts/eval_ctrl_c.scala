import better.files._
import joblist.{JobConfiguration, JobList}


val wd = (home / "ctrl_c_test").createIfNotExists(true)

val jl = new JobList(wd / ".run_and_wait")
//    val jl = JobList(wd / ".unit_jobs_test")


val tasks = for (i <- 1 to 3) yield {
  JobConfiguration(s"""sleep 200; echo "this is task $i" > task_$i.txt """, wd = wd)
}

jl.run(tasks)

System.exit(0)
