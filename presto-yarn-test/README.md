# Presto slider package - product tests


## Pre-requisites

In order to run product tests you need to have: 

 * a provisioned cluster with java 8 pre-installed. The cluster also needs HDP 2.2 and any other pre-requisites mentioned at presto-yarn/README.md. For HDP installation, we recommend installing it the standard way/locations. We expect the hadoop configuration to be at ```/etc/hadoop/conf``` and use ```/etc/init.d/hadoop-yarn*``` scripts to manage yarn processes in the cluster.

 * the appConfig.json and resources*.json used by the tests (available under src/test/resources/) are designed for a 4 node cluster (1 master and 3 slaves). If you have a different cluster configuration please update the .json accordingly and run ```mvn install``` prior to running product tests

 * all the nodes of cluster have to be accessible from your local machine, so you can update your /etc/hosts file with ip-hostname mappings for all nodes in the cluster

 * make sure that network locations (like a property ```yarn.resourcemanager.address, yarn.resourcemanager.scheduler.address, slider.zookeeper.quorum```) from ```src/test/resources/slider/conf/slider-client.xml``` file are accessible from your local machine

 * set a password for yarn user on master node. Create a ```src/test/resources/tempto-configuration-local.yaml``` yarn@master password and a private key to connect any cluster node as root user, example settings:

```
ssh:
  identity: path_to_your_key/insecure_key.pem
  roles:
    yarn:
      ...
      password: yarn
```

 * also replace ${PRESTO_YARN_ROOT} under hive:jdbc_url to point to your ```presto-yarn``` workspace in the ```src/test/resources/tempto-configuration-local.yaml``` file created

## Execution

To run product tests you need to enable maven profile ```productTests``` and then all product tests will be executed run during ```mvn test``` phase.
```
mvn test -PproductTests
```

## Debugging

```
mvn test -PproductTests -Dmaven.surefire.debug
```

## Running single test (single method)

```
mvn test -PproductTests -Dtest=Presto*#mutli* (Use -DfailIfNoTests=false flag if necessary)
```

## Troubleshooting

 * In case you run a lot tests in row it is possible that hdfs usercache is getting so large causing yarn node go into state UNHEALTHY, to fix that run (replace the paths, if needed, as per your hadoop configuration):

```
for node in master slave{1,2,3}; do
 ssh $node 'rm -rf  /mnt/hadoop-hdfs/nm-local-dir/*cache*'
 ssh $node /etc/init.d/hadoop-yarn-nodemanager restart
done
```
