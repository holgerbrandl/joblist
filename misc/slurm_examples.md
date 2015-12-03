## Job Monitoring

```
jobFile=test_job.sh
echo '#!/bin/bash -l' > $jobFile; echo 'sleep 30; touch test_job.txt' >> $jobFile
#sbatch -p haswell -J test_job --ntasks=1 --cpus-per-task=8 --mem-per-cpu=1800 -e "$curChunk.err.log" --time=8:00:00 $jobFile #2>/dev/null

## same with sorted args
sbatch  -J test_job --ntasks=1 --cpus-per-task=1  --time=8:00:00 -p haswell --mem-per-cpu=1800 -e "$curChunk.err.log" $jobFile #2>/dev/null
```
Result is
```
Submitted batch job 1641423

```

Result is
```
             JOBID PARTITION     NAME     USER    STATE       TIME TIME_LIMI  NODES NODELIST(REASON)
           1092630 haswell64  blast__   brandl  RUNNING       0:04   8:00:00      1 taurusi5603
```


## Job Status Reporting


## Links

https://computing.llnl.gov/linux/slurm/faq.html
https://rc.fas.harvard.edu/resources/documentation/convenient-slurm-commands/
http://slurm.schedmd.com/

http://stackoverflow.com/questions/18200701/lsof-shows-a-file-as-deleted-but-i-can-still-see-it-in-file-system