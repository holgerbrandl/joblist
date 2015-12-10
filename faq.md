# FAQ

## How to enforce local scheduling on a cluster?
Define a bash variable named `JL_LOCAL_ONLY`:
```
export JL_LOCAL_ONLY=true
```
This will cause `jl` to use the local scheduler even if an actual queueing system is available. It will indicate this by printing the message
```
jl status
.jobs: Using local scheduler
...
```
It's even possible to jump between schedulers:
```
jl submit "sleep 100"
jl kill

## continue locally
export JL_LOCAL_ONLY=true
jl wait --resubmit_retry
```
This is especially handy to debug failing jobs.


To restore scheduler auto-detection, simply remove the variable with
```
unset JL_LOCAL_ONLY
```



## How to use jl to monitor a large number of jobs?

Since jl startup is limited by the underlying java VM, subsequent invocation might be too slow to monitor/submit large (ie >1k) jobs jobs

There are 2 options to overcome this limitation

* Batch submissions: `jl` can read job definitions from stdin. It expectes one job per line

	```
	for job_nr in $(seq 1 10); do
	    echo "sleepTime=$(perl -e 'print int(rand(20)) + 10'); sleep $sleepTime; echo slept for $sleepTime seconds in job $job_nr"
	done | jl submit --batch -
	```

* Loop redirection: To just monitor a large number of jobs simply [pipe](http://stackoverflow.com/questions/18612603/redirecting-output-of-bash-for-loop) your submission loop into jl

	```
	for job_nr in $(seq 1 10); do
	    bsub "sleep 10"
	done | jl submit add
	```