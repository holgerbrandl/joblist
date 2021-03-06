JobList
=======

[![Join the chat at https://gitter.im/holgerbrandl/joblist](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/holgerbrandl/joblist?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge) [![Build Status](https://travis-ci.org/holgerbrandl/joblist.svg)](https://travis-ci.org/holgerbrandl/joblist) [ ![Download](https://api.bintray.com/packages/holgerbrandl/mpicbg-scicomp/joblist/images/download.svg) ](https://bintray.com/holgerbrandl/mpicbg-scicomp/joblist/_latestVersion)

A task list manager for hpc-clusters. Among others it supports monitoring, automatic resubmission, profiling, and reporting of job lists.

JobList `jl` can submit, monitor and wait until an entire a list of clusters jobs has finished. It reports average runtime statistics, and predicts the remaining runtime of a joblist based on cluster load and job complexities. `jl` can recover crashed jobs and resubmit them again using a customizable set of resubmission strategies.

Conceptually `jl` is *just* managing lists of job-ids as reported by the underlying queuing system. Currently [LSF](https://en.wikipedia.org/wiki/Platform_LSF), [slurm](http://slurm.schedmd.com/) but also any computer
(by means of a bundled [local multi-threading scheduler](https://github.com/holgerbrandl/joblist/blob/master/src/main/scala/joblist/local/LocalScheduler.scala)) are supported to process job lists.

Installation
------------

```bash
cd ~/bin
wget https://github.com/holgerbrandl/joblist/releases/download/v0.7.1/joblist_installer_v0.7.1.tar.gz 
tar -zxvf joblist_installer_v0.7.1.tar.gz

# You also may want to update your bash profile to includ jl in your PATH by default
echo '
export PATH='$(pwd)/joblist_v0.7.1':$PATH
' >> ~/.bash_profile
source ~/.bash_profile
```

Java8 is required to run JobList. To create (optional but recommenced) html reports [R](https://www.r-project.org/) (v3.2) and [pandoc](http://pandoc.org/) ([static build](https://github.com/jgm/pandoc/issues/11)) are needed.


Basic Usage
-----------


```
> jl --help
Usage: jl <command> [options] [<joblist_file>]

Supported commands are
  submit    Submits a job to the underlying queuing system and adds it to the list
  add       Extracts job-ids from stdin and adds them to the list
  wait      Wait for a list of jobs to finish
  resub     Resubmit non-complete jobs with escalated scheduler parameters
  status    Prints various statistics and allows to create an html report for the list
  cancel    Removes all  jobs of this list from the scheduler queue
  up        Moves a list of jobs to the top of a queue (if supported by the underlying scheduler)
  reset     Removes all information related to this joblist.

If no <joblist_file> is provided, jl will use '.jobs' as default, but to save typing it will remember
the last used joblist instance per directory.
```
All sub-commands provide more specific information (e.g.  `jl submit --help`)

The basic workflow is as follow:

1) Submit some jobs

```
jl submit "sleep 10"          ## add a job
jl submit "sleep 1000"        ## add another which won't finish in our default queue
```

2) Wait for them to finish
```
jl wait
> 2 jobs in total;   0.0% complete; Remaining time       <NA>;    0 done;    0 running;    2 pending;    0 killed;    0 failed
> 2 jobs in total;   0.0% complete; Remaining time       <NA>;    0 done;    2 running;    0 pending;    0 killed;    0 failed
> 2 jobs in total;  50.0% complete; Remaining time       ~10S;    1 done;    1 running;    0 pending;    0 killed;    0 failed
> 2 jobs in total;  50.0% complete; Remaining time       ~10S;    1 done;    0 running;    0 pending;    1 killed;    0 failed
```

3) Report status, render html-report and log information with
```
jl status
> 2 jobs in total;  50.0% complete; Remaining time       ~10S;    1 done;    0 running;    0 pending;    1 killed;    0 failed

jl status --report
> .jobs: Exported statistics into .jobs.{runinfo|jc}.log
> .jobs: Rendering HTML report... done
```

4) Resubmit non-complete jobs by escalating their scheduler configuration
```
## to different queue
jl resub --queue "long"
## or with 10h
jl resub --time "10:00"
```

By using `jl` workflows will be decoupled from the underlying queuing system.
Ie. `jl`-_ified_ workflows would run on a slurm system, an LSF cluster or simply locally on any desktop machine.

API Usage
---------

In addition to the provided shell utilities, joblist can be also used programatically in Java, Scala, Kotlin and other JVM languages. To get started simply add it as a dependency via [BinTray](https://bintray.com/holgerbrandl/mpicbg-scicomp/joblist):

```
<dependency>
  <groupId>de.mpicbg.scicomp</groupId>
  <artifactId>joblist</artifactId>
  <version>0.7.1</version>
  <type>pom</type>
</dependency>
```

Shown below is a [Scala](http://www.scala-lang.org/) example that auto-detects the used scheduler (slurm, lsf, or simple multi-threading as fallback), submits some jobs, waits for all of them to finish, and resubmits failed ones again to another queue:
```
import joblist._


val jl = JobList()

jl.run(JobConfiguration("echo foo"))
jl.run(JobConfiguration("echo bar"))

// block execution until are jobs are done
jl.waitUntilDone()

// optionally we could investigate jobs that were killed by the queuing system
val killedInfo: List[RunInfo] = jl.killed.map(_.info)

// resubmit to other queue
jl.resubmit(new OtherQueue("long"))

```

To use joblist with [Kotlin](https://kotlinlang.org/), we suggest using a little [support API](misc/kotlin_support_api/src/main/kotlin/JoblistSupportAPI.kt) which is available as an artifact under
```
de.mpicbg.scicomp.joblist:joblist-kotlin:1.1
```
See [joblist_test.kts](misc/kotlin_support_api/joblist_test.kts) for an example made with [kscript](https://github.com/holgerbrandl/kscript).

Support & Documentation
-----------------------

Feel welcome to submit pull-requests or tickets,  or simply get in touch via gitter (see button on top).

* [JobList Introduction](http://holgerbrandl.github.io/joblist/joblist_intro/joblist_intro.html) A presentation from December 2015 ([sources](./docs/joblist_intro/joblist_intro.md))
* [JobList User Guide](./docs/user_guide.md) for detailed information about the model behind `joblist` and how to use it
* [FAQ](./docs/faq.md)
* [Developer Information](./docs/devel_joblist.md) with details about to build, test, release and improve `joblist`


Related Projects
----------------


* [para](https://github.com/hillerlab/ParasolLSF/) is a parasol-like wrapper around LSF for efficiently handling batches of jobs on a compute cluster
* [Snakemake](https://bitbucket.org/johanneskoester/snakemake/wiki/Home)  is a workflow management system
* [lsf_utils](https://github.com/holgerbrandl/datautils/blob/master/bash/lsf_utils.sh) is a collections of bash functions to manage list of lsf-cluster jobs
* [Queue](https://www.broadinstitute.org/gatk/guide/topic?name=queue) is a command-line scripting framework for defining multi-stage genomic analysis pipelines combined with an execution manager
* [DRMAA](https://en.wikipedia.org/wiki/DRMAA) is a high-level API specification for the submission and control of jobs to a distributed resource management (DRM) system
* [sbatch_run](http://stackoverflow.com/a/34232712/590437) script takes a job name and your command in quotes, creates the script, and runs it (Slurm only)
* [Redis](https://dzone.com/articles/distributed-tasks-execution-and-scheduling-in-java) allows to schedule tasks through the standard JDK ExecutorService and ScheduledExecutorService API, with submitted tasks being executed on Redisson nodes.