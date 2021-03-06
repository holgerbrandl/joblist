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

    Intellij can sync to symbolic link as well (don't forget to exlude target and .git)
    ```
    mkdir /tmp/jl_devel
    ln -s /tmp/jl_devel ~/jl_devel
    # sync in intellij to jl_devel as remote path with user home being the origin of the sync configuration
    cd /tmp/jl_devel
    sbt clean assembly
    ```

## How to test?

See http://www.scala-sbt.org/0.13/docs/Testing.html

* The `unit_tests` must not be located under an NFS filesystem. This is because of [JDK File.exists() bug when using NFS](http://stackoverflow.com/questions/3833127/alternative-to-file-exists-in-java)

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
sbt
## see http://stackoverflow.com/questions/11159953/scalatest-in-sbt-is-there-a-way-to-run-a-single-test-without-tags
testOnly *TestCLI -- -z done
testOnly *SchedulingTest -- -z proper

## this does not seem to work
sbt testOnly joblist.ReportingTest

```
Tests are supposed to run locally or with any of the supported queuing system. Queuing system specific tests test for
the queue at the beginning of the test and are considered as passed on other platforms.

## Travis CI Integration

For details and examples see
* https://docs.travis-ci.com/user/languages/scala
* https://github.com/twitter/scalding/blob/master/.travis.yml
* http://conda.pydata.org/docs/travis.html (PATH)

To speed up build consider caching
https://blog.travis-ci.com/2013-12-05-speed-up-your-builds-cache-your-dependencies/


## Release Check List

Before getting started:
* make sure that `joblist/JobListCLI.scala:99` is commented out
* make sure to now use any non-public SNAPSHOT dependencies


1) Increment version in [`build.sbt`](../build.sbt), [JobListCLI](../src/main/scala/joblist/JobListCLI.scala#L25-L28)
2) Update version in README.md (installation and sbt inclusion)
2) Push and and create version on github
3) Build assembly jar, build tar.gz with
```bash
cd /dir/with/joblist
rm -rf target ## to get rid of old version-tag assebmly

sbt assembly

version=$(grep "val version" src/main/scala/joblist/JobListCLI.scala | cut -d' ' -f6 | tr -d '"')

mkdir joblist_v${version}

#cp target/scala-2.11/joblist-assembly-*.jar joblist_v${version}/joblist_assembly.jar
cp target/joblist-assembly-*.jar joblist_v${version}/joblist_assembly.jar
ll joblist_v${version}/joblist_assembly.jar

echo '#!/usr/bin/env bash' > joblist_v${version}/jl
echo 'java -Xmx1g -cp "$(dirname $0)/joblist_assembly.jar" joblist.JobListCLI "$@"' >> joblist_v${version}/jl
chmod ugo+x joblist_v${version}/jl

tar -cvzf joblist_installer_v${version}.tar.gz  joblist_v${version}
```


4. Create github release with attached bin-release

```bash
source /Users/brandl/Dropbox/archive/gh_token.sh
export GITHUB_TOKEN=${GH_TOKEN}
#echo $GITHUB_TOKEN

# make your tag and upload

jl_version=$(grep "val version" src/main/scala/joblist/JobListCLI.scala | cut -d' ' -f6 | tr -d '"')
echo $jl_version


#git tag v${jl_version} && git push --tags
(git diff --exit-code && git tag v${jl_version})  || echo "could not tag current branch"
git push --tags

# check the current tags and existing releases of the repo
github-release info -u holgerbrandl -r joblist

# create a formal release
github-release release \
    --user holgerbrandl \
    --repo joblist \
    --tag "v${jl_version}" \
    --name "v${jl_version}" \
    --description "See [Changes.md](https://github.com/holgerbrandl/joblist/blob/master/Changes.md) for changes." 


## upload sdk-man binary set
ll joblist_installer_v${version}.tar.gz
github-release upload \
    --user holgerbrandl \
    --repo joblist \
    --tag "v${jl_version}" \
    --name "joblist_installer_v${version}.tar.gz" \
    --file joblist_installer_v${version}.tar.gz

```

3. Create new version on [jcenter](https://bintray.com/holgerbrandl/mpicbg-scicomp/joblist/view`)
```
sbt publish

# locate jar, sources.jar and pom for new version in
open ~/.m2/repository/de/mpicbg/scicomp/joblist
```

Upload path (see crossPath notes in build.sbt)
```
de/mpicbg/scicomp/joblist/0.X
```
(todo use https://github.com/softprops/bintray-sbt)

### Post-release

Inc version to 1.x-SNAPSHOT in build.sbt and JobListCLI
