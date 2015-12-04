#!/usr/bin/env bash


## First we need sbt which is use to build jl
## It can be installed via conscript
## See http://www.scala-sbt.org/0.13/docs/Scripts.html
curl https://raw.githubusercontent.com/n8han/conscript/master/setup.sh | sh
cs sbt/sbt --branch 0.13.9



##
## Some build dependencies are noy yet in public repositories
##


jl_deps_dir=~/jl_deps
mkdir $jl_deps_dir
cd $jl_deps_dir


## Install docopt.java

wget http://ftp.halifax.rwth-aachen.de/apache/maven/maven-3/3.3.9/binaries/apache-maven-3.3.9-bin.tar.gz

tar -xvzf apache-*
export PATH=$(readlink -f apache-maven-3.3.9/bin):$PATH

cd $jl_deps_dir && git clone https://github.com/docopt/docopt.java.git
cd docopt.java
mvn clean install -Dmaven.test.skip=true



## Install scalautils (see https://github.com/holgerbrandl/scalautils)

cd $jl_deps_dir
git clone https://github.com/holgerbrandl/scalautils.git
cd scalautils
sbt publishLocal


## optionally remove the directory since the dependencies are now in .ivy2 and .mvn
#cd $jl_deps_dir && cd .. && rm -rf $jl_deps_dir


## (Optionally) install rend.r
## see https://github.com/holgerbrandl/datautils/tree/master/R/rendr)
##
## jl requires it only when rendering joblist reports

targetDirectory=~/bin/rendr
mkdir -p $targetDirectory
wget -NP $targetDirectory --no-check-certificate https://raw.githubusercontent.com/holgerbrandl/datautils/master/R/rendr/rend.R
chmod +x $targetDirectory/rend.R
echo 'export PATH='"$targetDirectory"':$PATH' >> ~/.bash_profile

#source ~/.bash_profile
which rend.R
ls ~/bin/rendr
~/bin/rendr/rend.R

