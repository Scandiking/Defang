#!/bin/sh
# Gradle start-up script for UNIX

APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`

# Default JVM options
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

MAX_FD="maximum"
warn () { echo "$*"; }
die () { echo; echo "$*"; echo; exit 1; }

OS="`uname`"
case "$OS" in
  Cygwin* ) cygwin=true ;;
  Darwin* ) darwin=true ;;
  MSYS* | MINGW* ) msys=true ;;
  NONSTOP* ) nonstop=true ;;
esac

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

exec "$JAVACMD" \
    $DEFAULT_JVM_OPTS \
    $JAVA_OPTS \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain "$@"
