#!/bin/bash -ex

# http://stackoverflow.com/questions/3572030/bash-script-absolute-path-with-osx
function absolutepath() {
    [[ $1 = /* ]] && echo "$1" || echo "$PWD/${1#./}"
}

function retry() {
  END=$(($(date +%s) + 600))

  while (( $(date +%s) < $END )); do
    set +e
    "$@"
    EXIT_CODE=$?
    set -e

    if [[ ${EXIT_CODE} == 0 ]]; then
      break
    fi
    sleep 5
  done

  return ${EXIT_CODE}
}

function hadoop_master_container(){
  compose ps -q hadoop-master
}

function check_hive() {
  # TODO use docker-compose
  docker exec $(hadoop_master_container) hive -e 'show tables'
}

function run_product_tests() {
  compose run runner \
    java -jar /workspace/target/presto-yarn-test-1.2-SNAPSHOT-executable.jar \
    --config-local /workspace/etc/docker/tempto-configuration-docker-local.yaml \
    $*
}

# docker-compose down is not good enough because it's ignores services created with "run" command
function stop_container() {
  SERVICE_NAME=$1
  CONTAINER_IDS=$(compose ps -q ${SERVICE_NAME})
  for CONTAINER_ID in $CONTAINER_IDS; do
    echo "Stopping and removing ${SERVICE_NAME} with id ${CONTAINER_ID}"
    docker stop ${CONTAINER_ID}
    docker rm ${CONTAINER_ID}
  done
}

function cleanup() {
  OLD_ENVIRONMENT=$ENVIRONMENT
  for ENVIRONMENT in hdp2.3 cdh5; do
    # stop application runner containers started with "run"
    stop_container runner

    # stop containers started with "up"
    compose down || true
  done
  ENVIRONMENT=$OLD_ENVIRONMENT

  # wait for docker logs termination
  wait
}

function compose() {
  docker-compose -f ${SCRIPT_DIR}/../etc/docker/$ENVIRONMENT/docker-compose.yml $*
}

SCRIPT_DIR=$(dirname $(absolutepath "$0"))
ENVIRONMENT=$1

if [[ "$ENVIRONMENT" != "cdh5" && "$ENVIRONMENT" != "hdp2.3" ]]; then
   echo "Usage: run_on_docker.sh <cdh5|hdp2.3> <product test args>" 
   exit 1
fi

shift 1

# check docker and docker compose installation
docker-compose version
docker version

cleanup

compose pull
compose build

compose up -d 
compose logs --no-color hadoop-master hadoop-slave1 hadoop-slave2 hadoop-slave3 &

retry check_hive

# run product tests
set +e
run_product_tests "$*"
EXIT_CODE=$?
set -x

cleanup

exit ${EXIT_CODE}
