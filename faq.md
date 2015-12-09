# FAQ

## How to enforce local schedulering one cluster?
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

