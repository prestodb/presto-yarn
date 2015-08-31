# Presto slider package - product tests


## Pre-requisites

In order to run product tests you need to have: 
 * a provisioned cluster which they will be run (e.g. an hfab vagrant or vsphere cluster) with java 8 preinstalled on the cluster
```
hfab vagrant.provision && hfab vagrant cloudera.install:java_version=8
# or
hfab vsphere.provision && hfab vsphere cloudera.install:java_version=8
```
 * all the nodes of cluster have to be accessible from your local machine, so you can update your /etc/hosts file with entities returned by the command
```
 hfab vagrant etc_hosts
 # or
 hfab vsphere etc_hosts
```
 * make sure that network locations (like a property ```yarn.resourcemanager.address, yarn.resourcemanager.scheduler.address, slider.zookeeper.quorum```) from ```src/test/resources/slider/conf/slider-client.xml``` file are accessible from your local machine
 * ```/var/presto``` directory created on all the nodes with ```yarn``` user as an owner
```
for node in master slave{1,2,3}; do ssh $node mkdir /var/presto; ssh $node chown yarn:yarn /var/presto; done 
```
 * HDFS home directory created for user yarn ```/user/yarn``` with ```yarn``` user as an owner
```
hadoop fs -mkdir -p /user/yarn
hadoop fs -chown yarn:yarn /user/yarn
```
 * set a password for yarn user on master node. Create a ```src/test/resources/tempto-configuration-local.yaml``` yarn@master password and a private key to connect any cluster node as root user, example settings:

```
ssh:
  identity: /home/kogut/teradata/hfab/hfab/util/pkg_data/insecure_key.pem
  roles:
    yarn:
      password: yarn
```

Note: vagrant cluster comes with old version of openssl library, please make sure you upgraded it before testing. To upgrade openssl run on all the nodes as root user:

```
for node in master slave{1,2,3}; do ssh $node yum upgrade openssl -y; done
```

Note: running tests on vsphere cluster locally for development could be a bad idea as it takes a lot of time to complete (beacause of network communication). In this case vagrant is advised.
 
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
mvn test -PproductTests -Dtest=Presto*#mutli*
```
