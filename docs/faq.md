# FAQ

## How to enforce local scheduling on a cluster?
Define a bash variable named `JL_FORCE_LOCAL`:
```
export JL_FORCE_LOCAL=true
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
export JL_FORCE_LOCAL=true
jl resub --retry
```
This is especially handy to debug failing jobs.


To restore scheduler auto-detection, simply remove the variable with
```
unset JL_FORCE_LOCAL
```


## How to use jl to monitor a large number of jobs?

See section _Batch Submission_ in the [user manual](./user_guide.md)