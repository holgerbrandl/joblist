# JobList Developer Info

We always welcome pull request and tickets.


## How to build


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

****
## Release Check List

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

5) Upload to jcenter (todo use https://github.com/softprops/bintray-sbt)

Post-release
6) Inc version to 1.x-SNAPSHOT in build.sbt and JobListCLI
7) Adjust dependent build.sbt's