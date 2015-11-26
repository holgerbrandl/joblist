1) Increment version in build.sbt, `joblist.JobListCLI.version`
2) Update version in README.md (installation and sbt inclusion)
2) Push and and create version on github
3) `sbt assembly` jar, build tar.gz with
```
cd /dir/with/joblist
sbt assembly
version=$(grep "val version" src/main/scala/joblist/JobListCLI.scala | cut -d' ' -f6)

mkdir joblist_v${version}


cp target/scala-2.11/joblist-assembly-0.1-SNAPSHOT.jar joblist_v${version}/joblist_assembly.jar
cp scripts/jl joblist_v${version}

tar -cvzf joblist_installer_v${version}.tar.gz  joblist_v${version}
```
 and attach to release

4) Upload to jcenter

Post-release
5) set version to 1.x-SNAPSHOT
6) Adjust dependent build.sbt's