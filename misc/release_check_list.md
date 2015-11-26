1) Increment version in build.sbt, `joblist.JobListCLI.version`
2) Update version in README.md (installation and sbt inclusion)
2) Push and and create version on github
3) `sbt assembly` jar, build tar.gz with
```
cd /dir/with/joblist

sbt assembly

version=$(grep "val version" src/main/scala/joblist/JobListCLI.scala | cut -d' ' -f6 | tr -d '"')

mkdir joblist_v${version}

cp target/scala-2.11/joblist-assembly-*.jar joblist_v${version}/joblist_assembly.jar
cp scripts/jl joblist_v${version}

tar -cvzf joblist_installer_v${version}.tar.gz  joblist_v${version}

cp joblist_installer_v${version}.tar.gz /Users/brandl/Dropbox/Public/joblist_releases
```
4) Attach installer to release

5) Upload to jcenter (todo use https://github.com/softprops/bintray-sbt)

Post-release
6) Inc version to 1.x-SNAPSHOT in build.sbt and JobListCLI
7) Adjust dependent build.sbt's