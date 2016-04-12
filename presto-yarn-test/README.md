# Presto slider package - product tests


## Pre-requisites

In order to run product tests you need to have: 

 * docker (tested with 1.10)

 * docker-compose (tested with 1.6.2)

## Execution

Execution will spin up a docker cluster for hadoop with yarn. Such cluster consists of 4 docker containers with several jvm processes in it, so it is strongly recommended to run these tests on a highly capable workstation.

Before you run any product test you need to build test binaries:

```
mvn clean package
```

### Execution profiles

There are two profiles which can be used for testing: 
 - cdh5 - Cloudera distribution of hadoop
 - hdp2.3 - Hortonworks distribution of hadoop

> Note that there are two tests which fails for hdp2.3. They are marked with `hdp2.3_quarantine` test group.

### Execution with automation script

```
bin/run_on_docker.sh <profile>
```

## Manual execution

```
cd presto-yarn-test/etc/docker/<profile>
docker-compose up -d
# wait until everything get ready
docker-compose run runner java -jar /workspace/target/presto-yarn-test-1.2-SNAPSHOT-executable.jar --config-local /workspace/etc/docker/tempto-configuration-docker-local.yaml
```

## Debugging product tests 

```
cd presto-yarn-test/etc/docker/<profile>
docker-compose up -d
# wait until everything get ready
docker-compose run -p 5005:5005 runner java -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005 -jar /workspace/target/presto-yarn-test-1.2-SNAPSHOT-executable.jar --config-local /workspace/etc/docker/tempto-configuration-docker-local.yaml
```
