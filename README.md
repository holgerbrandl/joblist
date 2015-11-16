JobList
=======


An hpc-ready task list manager. Among others it supports monitoring, automatic resubmission, profiling of job lists.

JobList `jl` can submit, monitor and wait until an entire a list of clusters jobs is finished. It can output average runtime statistics and estimates when the entire jobList should be finished. 'jl' can recover crashed jobs and submit them again.

Joblists are simply lists of job-ids

```
jl --help
jl --version
jl wait
jl check
jl totop

etc.
```

Jobs can be

a task runner with a Scala API to write HPC workflows

`


How to assemble?
-----------------


```
sbt assembly
```
This will require bsub to be availabe since we run some tests prior to packageing



Shell Integration
------------------

## todo continue this

```

jarFile=/home/brandl/Dropbox/cluster_sync/scalautils/target/scala-2.11/scalautils-assembly-0.1-SNAPSHOT.jar
launcherScript=...


export PATH=/home/brandl/Dropbox/cluster_sync/scalautils/src/main/scala/scalautils/tasks:$PATH
/Users/brandl/Dropbox/cluster_sync/scalautils/src/main/scala/scalautils/tasks

joblist(){
    jl add $*
}

wait4jobs(){
    jl wait $*
}


```


Misc
--------


How to run?
```
sbt assembly
java -cp ./target/scala-2.11/scalautils-assembly-0.1-SNAPSHOT.jar scalautils.tasks.JobListCLI
```

Related Projects
----------------


* [para](https://github.com/hillerlab/ParasolLSF/)