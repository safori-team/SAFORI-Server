#!/bin/sh
set -eu

exec java -Duser.timezone=Asia/Seoul ${JAVA_OPTS:-} -jar /app/app.jar
