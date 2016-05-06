JobList Manual
==============

__Work in progress!__

Feel welcome to submit a trouble ticket if you think we should write/rewrite/extend one of its section now.

Basic Workflow
=============

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
jl resub --queue "long" ## wait and resubmit failing jobs to another queue
```

By using `jl` workflows will be decoupled from the underlying queuing system.
Ie. `jl`-_ified_ workflows would run on a slurm system, an LSF cluster or simply locally on any desktop machine.


Core Commands
=============

`jl submit`
-----------

Submit jobs to a queuing system or to a local scheduler


`jl wait`
-----------

Wait for lists of jobs to finish


`jl add`
-----------

Monitor job submissions.


Submit some jobs with bsub/sbatch as you're used to and use jl for blocking and monitoring and final status handling:
```
bsub "echo foo" | jl add
bsub "echo bar" | jl add
bsub "exit 1"   | jl add

jl wait

if [ -n "$(jl status --failed)" ]; then
    echo "some jobs failed"
fi

## print captured sterr to understand why they did fail
jl status --failed --logs err
```

All `jl` commands use `.jobs` as a default list, but you can provide your own for clarity:
```
bsub "sleep 3" | jl add .other_jobs
```

Or to give a slurm example:
```
echo '#!/bin/bash
touch test.txt' | sbatch -p my_queue -J test_job --time=00:20 | jl add
jl wait --report
```


### Batch Submission

Since jl startup is limited by the underlying java VM, subsequent invocation might be too slow to monitor/submit large (ie >1k) jobs jobs

There are 2 options to overcome this limitation

* Batch submissions: `jl` can read job definitions from stdin (or from a file as well). By default it expects one job per line

	```
	for job_nr in $(seq 1 10); do
	    echo "sleepTime=$(perl -e 'print int(rand(20)) + 10'); sleep $sleepTime; echo slept for $sleepTime seconds in job $job_nr"
	done | jl submit --batch -
	```
	`joblist` also allows to use a custom separator charactor to process multi-line commands with this pattern

    ```
    for job_nr in $(seq 1 10); do
        echo "
        ## another job nr${job_nr}
        sleepTime=$(perl -e 'print int(rand(20)) + 10');
        sleep $sleepTime;
        echo slept for $sleepTime seconds in job $job_nr
        " | sed 's/^ *//' ## delete leading whitespace to allow for more robust regex
	done | jl submit --batch - --bsep '^##'
    ```

* Loop redirection: To just monitor a large number of jobs simply [pipe](http://stackoverflow.com/questions/18612603/redirecting-output-of-bash-for-loop) your submission loop into jl

	```
	for job_nr in $(seq 1 10); do
	    bsub "sleep 10"
	done | jl submit add
	```

Job resubmission with `jl resub`
-----------

Jobs are resubmitted by escalatint their base configuration.

Note: **Just** jobs are submitted with `jl submit` they can also be resubmitted in case they fail. When using `jl add` resubmission with `jl resub` is not possible.

Reporting with `jl status`
-----------

### HTML Report

### Tabular Report

### Logs report


Misc
-----------

### Global Configuration

`jl` can be globally configured by exporting some shell variables prior to launching `jl`.

* `JL_FORCE_LOCAL`: Force local scheduler
* `JL_DISABLE_REMEMBER_ME` tbd
* `JL_MAX_LOCAL_JOBS`:  maximum number of  concurrent local jobs. Just affects local scheduler.


