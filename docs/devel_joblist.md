# JobList Developer Info

If you just would like to use `jl` without developing it, see the [User Guide](./user_guide.md) instead.


## How to build?


To build the jar
```
sbt assembly
```

To publish the jar to the local maven and ivy index do
```
sbt assembly publishLocal publish
```

To clean dependency cache and previous build use
```
sbt clean
```
See [here](http://stackoverflow.com/questions/17190755/why-sbt-runs-dependency-resolution-every-time-after-clean) for details

To create an interactive console do
```
sbt test:console
```

### Issues:

1) It does not build when using NFS?

    * see http://stackoverflow.com/questions/17676336/no-locks-available-when-run-sbt-cmd
    * see http://stackoverflow.com/questions/29593153/sbt-forces-file-system-locking-even-on-distributed-file-systems
    * related ticket https://github.com/sbt/sbt/issues/2222

    Only known workaround: build under non-nfs FS, eg. under `/tmp` instead
    ```
    rsync -avs ~/Dropbox/cluster_sync/joblist /tmp/
    cd /tmp/joblist
    sbt clean assembly
    ```

## How to test?

Since some tests require an the assembled CLI make sure to prepare it first and add it to your PATH before running the
tests
```
sbt assembly
export PATH=$(pwd)/scripts:$PATH # .. which contains a jl launcher thats referring to the assembled jar in ./target/...

## run all tests
sbt test

## to run just one of the test classes do
## see http://stackoverflow.com/questions/6997730/how-to-execute-tests-that-match-a-regular-expression
sbt testOnly joblist.*CLI
sbt testOnly joblist.ReportingTest

```
Tests are supposed to run locally or with any of the supported queuing system. Queuing system specific tests test for
the queue at the beginning of the test and are considered as passed on other platforms.


## Release Check List

Before getting started:
* make sure that `joblist/JobListCLI.scala:99` is commented out
* make sure to now use any non-public SNAPSHOT dependencies


1) Increment version in build.sbt, `joblist.JobListCLI.version`
2) Update version in README.md (installation and sbt inclusion)
2) Push and and create version on github
3) Build assembly jar, build tar.gz with
```
cd /dir/with/joblist
rm -rf target ## to get rid of old version-tag assebmly

sbt assembly

version=$(grep "val version" src/main/scala/joblist/JobListCLI.scala | cut -d' ' -f6 | tr -d '"')

mkdir joblist_v${version}

cp target/scala-2.11/joblist-assembly-*.jar joblist_v${version}/joblist_assembly.jar

echo '#!/usr/bin/env bash' > joblist_v${version}/jl
echo 'java -Xmx1g -cp "$(dirname $0)/joblist_assembly.jar" joblist.JobListCLI "$@"' >> joblist_v${version}/jl
chmod ugo+x joblist_v${version}/jl

tar -cvzf joblist_installer_v${version}.tar.gz  joblist_v${version}

cp joblist_installer_v${version}.tar.gz /Users/brandl/Dropbox/Public/joblist_releases
```
4) Attach installer to release

5) Create new version on [jcenter](https://bintray.com/holgerbrandl/mpicbg-scicomp/joblist/view`
)
```
sbt publish

# locate jar, sources.jar and pom for new version in
open ~/.m2/repository/de/mpicbg/scicomp/joblist_2.11
```

Upload path (see crossPath notes in build.sbt)
```
de/mpicbg/scicomp/joblist/0.X
```
(todo use https://github.com/softprops/bintray-sbt)

### Post-release

Inc version to 1.x-SNAPSHOT in build.sbt and JobListCLI
