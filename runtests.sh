#!/bin/bash

set -v

if [[ -z "${JAVA_HOME_17_X64}" ]]; then
  JAVA_HOME_17_X64=C:/PROGRA~1/Java/jdk-17
fi
if [[ -z "${JAVA_HOME_24_X64}" ]]; then
  JAVA_HOME_24_X64=C:/PROGRA~1/Java/jdk-24
fi

JAVA_HOME_17_EXEC=${JAVA_HOME_17_X64}/bin/java.exe
JAVA_HOME_24_EXEC=${JAVA_HOME_24_X64}/bin/java.exe

if [ ! -f ${JAVA_HOME_17_EXEC} ]; then
  JAVA_HOME_17_EXEC=${JAVA_HOME_17_X64}/bin/java
fi
if [ ! -f ${JAVA_HOME_24_EXEC} ]; then
  JAVA_HOME_24_EXEC=${JAVA_HOME_24_X64}/bin/java
fi

if [ ! -f ${JAVA_HOME_17_EXEC} ]; then
  echo "There is no Java 17 executable in ${JAVA_HOME_17_X64}"
  exit 1
fi
if [ ! -f ${JAVA_HOME_24_EXEC} ]; then
  echo "There is no Java 24 executable in ${JAVA_HOME_24_X64}"
  exit 1
fi


mvn clean package -DskipTests || exit 1

# Java 17 with jar files in class path

$JAVA_HOME_17_EXEC \
  -Djava.security.manager -Djava.security.policy==security.policy \
  -XX:-EnableDynamicAgentLoading -Xshare:off -ea \
  -Dnet.bytebuddy.safe=true -javaagent:lib/byte-buddy-agent-1.15.11.jar \
  --add-reads java.base=ALL-UNNAMED \
  -cp target/permcheck-0.0.1-SNAPSHOT.jar\;target/permcheck-0.0.1-SNAPSHOT-tests.jar\;lib/byte-buddy-1.15.11.jar\;lib/byte-buddy-agent-1.15.11.jar\;lib/junit-4.12.jar\;lib/hamcrest-core-1.3.jar \
  main.TestMain \
  --permcheck.policy permcheck.policy \
  || exit 1

# Java 17 with class directories in class path

$JAVA_HOME_17_EXEC \
  -Djava.security.manager -Djava.security.policy==security.policy \
  -XX:-EnableDynamicAgentLoading -Xshare:off -ea \
  -Dnet.bytebuddy.safe=true -javaagent:lib/byte-buddy-agent-1.15.11.jar \
  --add-reads java.base=ALL-UNNAMED \
  -cp target/classes\;target/test-classes\;lib/byte-buddy-1.15.11.jar\;lib/byte-buddy-agent-1.15.11.jar\;lib/junit-4.12.jar\;lib/hamcrest-core-1.3.jar \
  main.TestMain \
  --permcheck.policy permcheck.policy \
  || exit 1

# Java 24 with jar files in class path

$JAVA_HOME_24_EXEC \
  -XX:-EnableDynamicAgentLoading -Xshare:off -ea \
  --sun-misc-unsafe-memory-access=deny \
  -Dnet.bytebuddy.safe=true -javaagent:lib/byte-buddy-agent-1.15.11.jar \
  --add-reads java.base=ALL-UNNAMED \
  -cp target/permcheck-0.0.1-SNAPSHOT.jar\;target/permcheck-0.0.1-SNAPSHOT-tests.jar\;lib/byte-buddy-1.15.11.jar\;lib/byte-buddy-agent-1.15.11.jar\;lib/junit-4.12.jar\;lib/hamcrest-core-1.3.jar \
  main.TestMain \
  --permcheck.policy permcheck.policy \
  || exit 1

set +v

echo "Success!"