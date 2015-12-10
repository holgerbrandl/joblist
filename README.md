JobList
=======

[![Join the chat at https://gitter.im/holgerbrandl/joblist](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/holgerbrandl/joblist?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)


A task list manager for hpc-clusters. Among others it supports monitoring, automatic resubmission, profiling, and reporting of job lists.

JobList `jl` can submit, monitor and wait until an entire a list of clusters jobs is finished. It can output average runtime statistics and estimates when the entire jobList should be finished. 'jl' can recover crashed jobs and submit them again.

Conceptually `jl` *just* manages lists of job-ids as reported by the underlying queuing system. Currently [LSF](https://en.wikipedia.org/wiki/Platform_LSF), [slurm](http://slurm.schedmd.com/) but also any computer
(by means of a bundled [local multi-threading scheduler](https://github.com/holgerbrandl/joblist/blob/master/src/main/scala/joblist/local/LocalScheduler.scala)) are supported to process job lists.


Installation
------------

```
cd ~/bin
curl https://dl.dropboxusercontent.com/u/113630701/joblist_releases/joblist_installer_v0.3.tar.gz | tar -zxvf -
echo 'export PATH='$(pwd)/joblist_v0.3':$PATH' >> ~/.bash_profile
source ~/.bash_profile
```

Java8 is required to run JobList. To create (optional but recommenced) html reports [R](https://www.r-project.org/) (v3.2) and [pandoc](http://pandoc.org/) ([static build](https://github.com/jgm/pandoc/issues/11)) are needed.


Basic Usage
-----------


```
> jl --help
Usage: jl <command> [options] [<joblist_file>]

Supported commands are
  submit    Submits a named job including automatic stream redirection and adds it to the list
  add       Extract job-ids from stdin and add them to the list
  wait      Wait for a list of tasks to finish
  status    Prints various statistics and allows to create an html report for the list
  kill      Removes all queued jobs of this list from the scheduler
  up        Moves a list of jobs to the top of a queue if supported by the underlying scheduler

If no <joblist_file> is provided, jl will use '.jobs' as default

```


Submit some jobs with bsub/sbatch as you're used to and use jl for blocking and monitoring and final status handling:
```
bsub "echo foo" | jl add
bsub "echo bar" | jl add
bsub "exit 1"   | jl add

jl wait

if [ -n "$(jl status --failed)" ]; then
    echo "some jobs failed"
fi

## which ones
jl status
```

Or to give a slurm example:
```
echo '#!/bin/bash
touch test.txt' | sbatch -p my_queue -J test_job --time=00:20 | jl add
jl wait --report
```

All `jl` commands use `.jobs` as a default list, but you can provide your own for clarity:
```
bsub "sleep 3" | jl add .other_jobs
```

If jobs are submitted with `jl` they can also be resubmitted in case they fail:
```
jl submit "sleep 10"          ## add a jobs
jl submit "sleep 1000"        ## add another which won't finish in our default queue
jl wait --resubmit_queue long ## wait and resubmit failing jobs to another queue
```
Another advantage when submitting jobs via `jl` is that it decouples workflows from the underlying queuing system.
Ie. the last example would run on a Slurm system, an LSF cluster or simply locally on any desktop machine.

API Usage
---------

In addition to the provided shell utilities, joblist is also usable programatically. To get started add it as a dependency to your build.sbt

```
libraryDependencies += "de.mpicbg.scicomp" %% "joblist" % "0.3"
```

Here's a [Scala](http://www.scala-lang.org/) example that auto-detects the used scheduler (slurm, lsf, or simple multi-threading as fallback), submits some jobs, waits for all of them to finish, and resubmits failed ones again to another queue:
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
jl.resubmit(new BetterQueue("long"))

```

How to build?
-----------------


To package into a stand-alone jar run
```
sbt assembly
```

To deploy into the local ivy-index run

```
sbt publishLocal
```

To run the test suite simply do
```
sbt test
```
The tests will auto-detect the queuing system or fall back to use a local scheduler in case auto-detection fails


Support
-------

Feel welcome to submit pull-requests or tickets,  or simply get in touch via gitter (see button on top).

Related Projects
----------------


* [para](https://github.com/hillerlab/ParasolLSF/) is a parasol-like wrapper around LSF for efficiently handling batches of jobs on a compute cluster
* [Snakemake](https://bitbucket.org/johanneskoester/snakemake/wiki/Home)  is a workflow management system
* [lsf_utils](https://github.com/holgerbrandl/datautils/blob/master/bash/lsf_utils.sh) A collections of bash functions to manage list of lsf-cluster jobs