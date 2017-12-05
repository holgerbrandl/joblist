

## Joblist v0.7.1 (5.12.2017)

* fixed compatbility problem with array jobs on slurm

## Joblist v0.7 (15.9.2016)

* `jl submit --wait` does not longer reset the list [#49](https://github.com/holgerbrandl/joblist/issues/49)
* Added `--reset` to `jl submit`
* Support  java-Xmx-notation for memory limits. Example: `-m 5g` [#56](https://github.com/holgerbrandl/joblist/issues/56)
* Streamlined report generation [#50](https://github.com/holgerbrandl/joblist/issues/50)
* Dramatically improved batch-submission performance
* More idiomatic blocking with `jl wait || exit 1` (or `jl status || exit 1`)
* Reduced # status files per job by 20% (4 instead of 5) on SLURM
* Validate that no jobs would pend forever due to `PartitonTimeLimt` on SLURM

## Joblist v0.6

Most significant new features in this release are

* New `--mem` and `--time` arguments for `jl submit` to adjust walltime and maximum memory per job
* Implement idiomatic way in `jl status` to output stderr/stdout-logs, job configuration, and runinfo ([#37](https://github.com/holgerbrandl/joblist/issues/37))
* Separated job resubmission into new command `jl resub`
* Allow multi-line commands to be submitted in batch mode ([#40](https://github.com/holgerbrandl/joblist/issues/40))
* Remember last used joblist in terminal ([#41](https://github.com/holgerbrandl/joblist/issues/41))
* Renamed `jl kill` to `jl cancel` to be more consistent with slurm/lsf naming conventions
* Automatic dependency resolution for report rendering (see [rend.R](https://github.com/holgerbrandl/datautils/tree/master/R/rendr))
* [JCenter integration](https://bintray.com/holgerbrandl/mpicbg-scicomp/joblist) and continuous integration using [TravisCI](https://travis-ci.org/holgerbrandl/joblist)
* Improved report to include summary table and job submission example

For a more complete list see the [milestone tickets](https://github.com/holgerbrandl/joblist/issues?utf8=%E2%9C%93&q=milestone%3Av0.6)