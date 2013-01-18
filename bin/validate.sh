#!/bin/sh
#
# verify an XML instance document using Apache Xerces
#
# validate [ -qh ] [ -S schemaLocFile ] xmlfile ...
#   -h      print this usage (ignore all other input)
#   -q      print nothing to standard out; only set the exit code
#   -s      print nothing to standard out or error; only set the exit code
#   -S schemaLocFile  set the schema cache via a schema location file
# Each line in a schemaLocFile gives a namespace, a space, and local file path.
# The file path is the location of the Schema (.xsd) document for that namespace.
#
# This wrapper script figures out where to find java and the Junx class files
# and runs the Validate application.  If $JAVA_HOME is the JVM used will be
# $JAVA_HOME/bin/java.  If that is not available, the java command will be 
# taken from the command search path, $PATH.
#
# If $JUNX_JAR is set, its value will be taken to be the Junx jar file 
# containing Validate application, and it will be used as the sole contents
# of the class path sent to the JVM.  If $JUNX_JAR is not set, 
# $JUNX_HOME/lib/junx.jar will be used instead if $JUNX_HOME exists.  If it 
# does not, the CLASS_PATH will be assumed to contain the required Junx 
# classes.
#
prog=$0

if [ -n "$JUNX_JAR" ]; then
    cp=$JUNX_JAR
elif [ -n "$JUNX_HOME" ]; then
    cp=$JUNX_HOME/lib/junx.jar
elif [ -n "$CLASSPATH" ]; then
    cp=$CLASSPATH
else
    bindir=`dirname $prog`/
    if [ -e "${bindir}../lib/junx.jar" ]; then
        cp=${bindir}../lib/junx.jar
    fi
fi

if [ -z "$cp" ]; then
    echo "Can't find junx.jar (and no CLASSPATH set)"
    exit 1
fi

bin=
if [ -n "$JAVA_HOME" ]; then
   bin=$JAVA_HOME/bin/
fi

# echo ${bin}java -cp $cp ncsa.xml.validation.Validate $*
exec ${bin}java -classpath $cp ncsa.xml.validation.Validate $*

