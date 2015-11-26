JobList
=======


A task list manager for hpc-clusters. Among others it supports monitoring, automatic resubmission, profiling, and reporting of job lists.

JobList `jl` can submit, monitor and wait until an entire a list of clusters jobs is finished. It can output average runtime statistics and estimates when the entire jobList should be finished. 'jl' can recover crashed jobs and submit them again.

JobLists are simple lists of job-ids as reported by the underlying queuing system. Currently [LSF](https://en.wikipedia.org/wiki/Platform_LSF), [slurm](http://slurm.schedmd.com/) and the local shell are supported as job runners.


Installation
------------

```
cd ~/bin
https://github.com/holgerbrandl/datautils/archive/v1.20.tar.gz  | tar -zxvf -
echo $(pwd)/joblist_v1.0:PATH >> ~/.bash_profile
```

Java8 is required to run JobList.

To use single verbs you can use some provided shortcuts by adding this to your bash_profile
```
eval "$(jl shortcuts)"
```

Basic Usage
-----------


```
jl --help
jl --version
```


Submit some jobs with busb as you're used to and use jl for blocking and monitoring and final status handling:
```
busb "hello foo" | jl add
busb "hello bar" | jl add

jl wait

if [ -n "$(jl failed)" ]; then
    echo "some jobs failed"
fi

```

All `jl` commands use `.jobs` as a default list, but you can provide your own for clarity:
```
busb "sleep 3" | jl add .other_jobs
```

If jobs are submitted with `jl` they can also be resubmitted in case they fail:
```
jl submit "sleep 10"          ## add a jobs
jl submit "sleep 1000"        ## add another which won't finish in our default queue
jl wait --resubmit_queue long ## wait and resubmit failing jobs to another queue
```


API Usage
---------

In addition to the provided shell utilities, joblist is also usable programatically. To get started add it as a dependency to your build.sbt

```
libraryDependencies += "de.mpicbg.scicomp" %% "joblist" % "0.1-SNAPSHOT"
```

Here's an example that auto-detects the used scheduler (slurm, lsf, or simple multi-threading as fallback ), submits some jobs, waits for all of them to finish, and resubmits failed ones again to another queue:
```
import joblist._


val jl = JobList()

jl.run(JobConfiguration("echo foo"))
jl.run(JobConfiguration("echo bar"))

// block execution until are jobs are done
jl.waitUntilDone()

// get the run information about jobs that were killed by the queuing system
val killedInfo: List[RunInfo] = jl.killed.map(_.info)

// resubmit to other queue
jl.resubmitKilled(new BetterQueue("long"))

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

To run the tests you either need `bsub` and some of the lsf tools in your path. Alternativly you can also use/source the provided [dummy tools](https://github.com/holgerbrandl/joblist/blob/master/scripts/fake_lsf.sh)



Related Projects
----------------


* [para](https://github.com/hillerlab/ParasolLSF/) is a parasol-like wrapper around LSF for efficiently handling batches of jobs on a compute cluster
* [Snakemake](https://bitbucket.org/johanneskoester/snakemake/wiki/Home)  is a workflow management system
* [lsf_utils](https://github.com/holgerbrandl/datautils/blob/master/bash/lsf_utils.sh) A collections of bash functions to manage list of lsf-cluster jobs