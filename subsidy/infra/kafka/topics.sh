#!/bin/sh
# Топики кейса (libs/common Topics): ключ — id агрегата, 3 партиции, хранение 7 дней.
set -eu
K=/opt/kafka/bin/kafka-topics.sh
for t in package.package.v1 package.file.v1 docproc.document.v1 rules.package.v1; do
  $K --bootstrap-server kafka:9092 --create --if-not-exists --topic "$t" --partitions 3 --replication-factor 1 --config retention.ms=604800000
done
$K --bootstrap-server kafka:9092 --list
