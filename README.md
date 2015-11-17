JobList
=======


An hpc-ready task list manager. Among others it supports monitoring, automatic resubmission, profiling of job lists.

JobList `jl` can submit, monitor and wait until an entire a list of clusters jobs is finished. It can output average runtime statistics and estimates when the entire jobList should be finished. 'jl' can recover crashed jobs and submit them again.

Joblists are simply lists of job-ids as reported by the underlying queuing system. Currently lsf, slurm and the local shell are supported as job runners.


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


Submit some jobs with busb as you're used to and use jl for blocking and monitoring:
```
busb "hello foo" | jl add
busb "hello bar" | jl add

jl wait
```

All `jl` commands use `.jobs` as a default list, but you can provide your own for clarity:
```
busb "sleep 3" | jl add .other_jobs
```

If jobs are submitted with `jl` they can also be resubmitted in case they fail:
```
jl submit ""
```


API Usage
---------

In addition to the provided shell utilities, joblist is also usable programmatically. Here's an example
```
TBD
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

To run the tests you either need `bsub` and some of the lsf tools in your path. Alternativly you can also use/source the provided (dummy tools)(https://github.com/holgerbrandl/joblist/blob/master/scripts/fake_lsf.sh)



Related Projects
----------------


* [para](https://github.com/hillerlab/ParasolLSF/) is a parasol-like wrapper around LSF for efficiently handling batches of jobs on a compute cluster
* [Snakemake](https://bitbucket.org/johanneskoester/snakemake/wiki/Home)  is a workflow management system
