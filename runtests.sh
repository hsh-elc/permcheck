#!/bin/bash

set -v

JAVA_HOME_17_EXEC=C:/PROGRA~1/Java/jdk-17/bin/java.exe
JAVA_HOME_24_EXEC=C:/PROGRA~1/Java/jdk-24/bin/java.exe

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