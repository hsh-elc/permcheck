#!/bin/bash

set -v

pwd
ls -a
ls -a target

case "$OSTYPE" in
  solaris*) pathsep=":" ;;
  darwin*)  pathsep=":" ;; 
  linux*)   pathsep=":" ;;
  bsd*)     pathsep=":" ;;
  msys*)    pathsep="\;" ;;
  cygwin*)  pathsep="\;" ;;
  *)        echo "unknown: $OSTYPE" ;;
esac

release=$1

if [[ -z "${release}" ]]; then
  release=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
fi

if [[ -z "${JAVA_HOME_17_X64}" ]]; then
  JAVA_HOME_17_X64=C:/PROGRA~1/Java/jdk-17
fi
if [[ -z "${JAVA_HOME_25_X64}" ]]; then
  JAVA_HOME_25_X64=C:/PROGRA~1/Java/jdk-25
fi

JAVA_HOME_17_EXEC=${JAVA_HOME_17_X64}/bin/java.exe
JAVA_HOME_25_EXEC=${JAVA_HOME_25_X64}/bin/java.exe

if [ ! -f ${JAVA_HOME_17_EXEC} ]; then
  JAVA_HOME_17_EXEC=${JAVA_HOME_17_X64}/bin/java
fi
if [ ! -f ${JAVA_HOME_25_EXEC} ]; then
  JAVA_HOME_25_EXEC=${JAVA_HOME_25_X64}/bin/java
fi

if [ ! -f ${JAVA_HOME_17_EXEC} ]; then
  echo "There is no Java 17 executable in ${JAVA_HOME_17_X64}"
  exit 1
fi
if [ ! -f ${JAVA_HOME_25_EXEC} ]; then
  echo "There is no Java 25 executable in ${JAVA_HOME_25_X64}"
  exit 1
fi


mvn clean package -DskipTests || exit 1

# Java 17 with jar files in class path

$JAVA_HOME_17_EXEC \
  -Djava.security.manager -Djava.security.policy==security.policy \
  -XX:-EnableDynamicAgentLoading -Xshare:off -ea \
  -Dnet.bytebuddy.safe=true -javaagent:lib/byte-buddy-agent-1.17.5.jar \
  --add-reads java.base=ALL-UNNAMED \
  -cp target/permcheck-${release}.jar${pathsep}target/permcheck-${release}-tests.jar${pathsep}lib/* \
  main.TestMain \
  --permcheck.policy permcheck.policy \
  || exit 1

# Java 17 with class directories in class path

$JAVA_HOME_17_EXEC \
  -Djava.security.manager -Djava.security.policy==security.policy \
  -XX:-EnableDynamicAgentLoading -Xshare:off -ea \
  -Dnet.bytebuddy.safe=true -javaagent:lib/byte-buddy-agent-1.17.5.jar \
  --add-reads java.base=ALL-UNNAMED \
  -cp target/classes${pathsep}target/test-classes${pathsep}lib/* \
  main.TestMain \
  --permcheck.policy permcheck.policy \
  || exit 1

# Java 25 with jar files in class path

$JAVA_HOME_25_EXEC \
  -XX:-EnableDynamicAgentLoading -Xshare:off -ea \
  --sun-misc-unsafe-memory-access=deny \
  -Dnet.bytebuddy.safe=true -javaagent:lib/byte-buddy-agent-1.17.5.jar \
  --add-reads java.base=ALL-UNNAMED \
  -cp target/permcheck-${release}.jar${pathsep}target/permcheck-${release}-tests.jar${pathsep}lib/* \
  main.TestMain \
  --permcheck.policy permcheck.policy \
  || exit 1

set +v

echo "Success!"