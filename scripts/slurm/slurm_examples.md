
## Job Cmd Piping

```
echo '#!/bin/bash
touch test.txt
' | sbatch -p haswell -J test_job --time=00:20
```

```
echo '#!/bin/bash
sleep 240
' | sbatch -p haswell -J test_job

squeue -lu $(whoami)

```

## Job Monitoring



```
jobFile=test_job.sh
echo '#!/bin/bash -l' > $jobFile; echo 'sleep 30; touch test_job.txt' >> $jobFile
#id=$(sbatch -p haswell -J test_job --ntasks=1 --cpus-per-task=8 --mem-per-cpu=1800 -e "$curChunk.err.log" --time=8:00:00 $jobFile  2>&1 | cut -d' ' -f4)
id=$(sbatch -p haswell -J test_job --time=00:20 $jobFile  2>&1 | cut -d' ' -f4)
echo $id

#scontrol show jobid -dd 1649829
sacct -j $id --format=JobID,JobName,Elapsed,End,Submit,Start,State,ExitCode,Timelimit

brandl@tauruslogin3:~/test> sacct -j $id --format=JobID,JobName,Elapsed,End,Submit,Start,State,ExitCode,Timelimit
       JobID    JobName    Elapsed                 End              Submit               Start      State ExitCode  Timelimit
------------ ---------- ---------- ------------------- ------------------- ------------------- ---------- -------- ----------
1650451        test_job   00:00:00             Unknown 2015-12-04T11:37:11             Unknown    PENDING      0:0   08:00:00

brandl@tauruslogin3:~/test> sacct -j $id --format=JobID,JobName,Elapsed,End,Submit,Start,State,ExitCode,Timelimit
       JobID    JobName    Elapsed                 End              Submit               Start      State ExitCode  Timelimit
------------ ---------- ---------- ------------------- ------------------- ------------------- ---------- -------- ----------
1650630        test_job   00:00:08             Unknown 2015-12-04T11:38:31 2015-12-04T11:38:51    RUNNING      0:0   00:01:00

       JobID    JobName    Elapsed                 End              Submit               Start      State ExitCode  Timelimit
------------ ---------- ---------- ------------------- ------------------- ------------------- ---------- -------- ----------
1650630        test_job   00:00:36 2015-12-04T11:39:27 2015-12-04T11:38:31 2015-12-04T11:38:51  COMPLETED      0:0   00:01:00
1650630.bat+      batch   00:00:36 2015-12-04T11:39:27 2015-12-04T11:38:51 2015-12-04T11:38:51  COMPLETED      0:0
brandl@tauruslogin3:~/test>


## Failing Job

```
jobFile=test_job.sh
echo '#!/bin/bash -l' > $jobFile; echo 'sleep 20; exit 5' >> $jobFile
id=$(sbatch -p haswell -J test_job --time=8:00:00 $jobFile 2>&1 | cut -d' ' -f4)
echo "id is $id"
sleep 1
sacct -j $id --format=JobID,JobName,Elapsed,End,Submit,Start,State

sleep 60
scontrol show jobid -dd $id
sacct -j $id --format=JobID,JobName,Elapsed,End,Submit,Start,State
```



## Walltime Killed

```
jobFile=test_job.sh
echo '#!/bin/bash -l' > $jobFile; echo 'sleep 20; exit 1' >> $jobFile
id=$(sbatch -p haswell -J test_job --time=00:20 $jobFile 2>&1 | cut -d' ' -f4)
echo "id is $id"

#sleep 60
#scontrol show jobid -dd $id
sacct -j $id --format=JobID,JobName,Elapsed,End,Submit,Start,State,ExitCode,Timelimit
```
       JobID    JobName    Elapsed                 End              Submit               Start      State ExitCode  Timelimit
------------ ---------- ---------- ------------------- ------------------- ------------------- ---------- -------- ----------
1650090       test_job2   00:01:01 2015-12-04T11:13:50 2015-12-04T11:12:45 2015-12-04T11:12:49    TIMEOUT      1:0   00:01:00
1650090.bat+      batch   00:01:01 2015-12-04T11:13:50 2015-12-04T11:12:49 2015-12-04T11:12:49  COMPLETED      0:0


## `sacct` fields

AllocCPUS        Account         AssocID      AveCPU
AveCPUFreq       AveDiskRead     AveDiskWrite AvePages
AveRSS           AveVMSize       BlockID      Cluster
Comment          ConsumedEnergy  CPUTime      CPUTimeRAW
DerivedExitCode  Elapsed         Eligible     End
ExitCode         GID             Group        JobID
JobIDRaw         JobName         Layout       MaxDiskRead
MaxDiskReadNode  MaxDiskReadTask MaxDiskWrite MaxDiskWriteNode
MaxDiskWriteTask MaxPages        MaxPagesNode MaxPagesTask
MaxRSS           MaxRSSNode      MaxRSSTask   MaxVMSize
MaxVMSizeNode    MaxVMSizeTask   MinCPU       MinCPUNode
MinCPUTask       NCPUS           NNodes       NodeList
NTasks           Priority        Partition    QOSRAW
ReqCPUFreq       ReqCPUs         ReqMem       Reservation
ReservationId    Reserved        ResvCPU      ResvCPURAW
Start            State           Submit       Suspended
SystemCPU        Timelimit       TotalCPU     UID
User             UserCPU         WCKey        WCKeyID




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