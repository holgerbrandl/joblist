
killByName(){
    echo killing jobs which include: $1 ...
    bjobs -w | grep $1 | awk '{ print $1 }'   | while read id ; do bkill $id ; done
}
export -f killByName


