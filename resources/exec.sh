#!/bin/sh
DIR=`dirname $0`
$DIR/java $JLINK_VM_OPTIONS -jar $DIR/../%s.jar $@
