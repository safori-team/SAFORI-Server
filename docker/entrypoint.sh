#!/bin/sh
set -eu

exec java -javaagent:/app/opentelemetry-javaagent.jar -Duser.timezone=Asia/Seoul ${JAVA_OPTS:-} -jar /app/app.jar
