# Presto slider package - product tests


## Pre-requisites

In order to run product tests you need to have: 

 * a provisioned Red Hat based cluster with java 8 pre-installed. The cluster also needs HDP 2.2+ or CDH 5.4+ and any other pre-requisites mentioned at presto-yarn/README.md. For HDP installation, we recommend installing it the standard way/locations. We expect the hadoop configuration to be at ```/etc/hadoop/conf``` and use ```/etc/init.d/hadoop-yarn*``` scripts to manage yarn processes in the cluster.

 * create a new file ```src/test/resources/tempto-configuration-local.yaml``` copying the sample at ```src/test/resources/tempto-configuration.yaml```. Set the ```master``` (coordinator for Presto) and a 'list' (follow yaml list format) of ```slaves``` (workers for Presto) hostnames under ```cluster```.

 * Yarn nodemanagers and any other pre-requisite service like Zookeeper should be running on all configured nodes.

 * the appConfig.json and resources-multinode.json used by the tests (available under src/main/resources/) are designed for a 4 node cluster (1 master and 3 slaves). If you have a different cluster configuration defined under ```cluster``` in your tempto-configuration-local.yaml, update the .json accordingly and run ```mvn install``` prior to running product tests

 * make sure that network locations (like a property ```yarn.resourcemanager.address, yarn.resourcemanager.scheduler.address, slider.zookeeper.quorum```) from ```src/test/resources/slider/conf/slider-client.xml``` file are accessible from your local machine

 * set a private key to connect any cluster node as root user, example settings:

```
ssh:
  identity: path_to_your_key/insecure_key.pem
```

 * set a number of virtual cores per node which is set in yarn on your cluster, by default it is set to ``8``
 
```
cluster:
  vcores: 8
```

> Note that running these tests may change the configuration of your cluster (services: yarn and cgroup, and yarm@master password).

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
mvn test -PproductTests -Dtest=Presto*#multi* (Use -DfailIfNoTests=false flag if necessary)
```

## Troubleshooting

 * In case you run a lot tests in row it is possible that hdfs usercache is getting so large causing yarn node go into state UNHEALTHY, to fix that run (replace the paths, if needed, as per your hadoop configuration):

```
for node in master slave{1,2,3}; do
 ssh $node 'rm -rf  /mnt/hadoop-hdfs/nm-local-dir/*cache*'
 ssh $node /etc/init.d/hadoop-yarn-nodemanager restart
done
```
