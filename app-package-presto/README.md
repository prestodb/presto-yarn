# Presto slider package

##Integration tests

### Pre-requisites

In order to run integration you need to have: 
 * a provisioned cluster which they will be run (e.g. an hfab vagrant or vsphere cluster) with java 8 preinstalled on the cluster
```
hfab vagrant.provision && hfab vagrant cloudera.install:java_version=8
# or
hfab vsphere.provision && hfab vsphere cloudera.install:java_version=8
```
 * all the nodes of cluster have to be accessible from your local machine, so you can update your /etc/hosts file with entires returned by the command
```
 hfab vagrant etc_hosts
 # or
 hfab vsphere etc_hosts
```
 * make sure that network locations (like a property ```yarn.resourcemanager.address, yarn.resourcemanager.scheduler.address, slider.zookeeper.quorum```) from ```src/test/resources/slider/conf/${your cluster}/slider-client.xml``` file are accessible from your local machine
 * ```/var/presto``` directory created on all the nodes with ```yarn``` user as an owner
 * HDFS home directory created for user yarn ```/user/yarn``` with ```yarn``` user as an owner

Note: vagrant cluster comes with old version of openssl library, please make sure you upgraded it before testing. To upgrade openssl run on all the nodes as root user:
```
yum update openssl
```
 

### Execution

To run integration tests you need to enable maven profile ```integration``` and then all integration tests will be run during ```mvn verify``` phase.

```
# with default cluster (vagrant)
mvn verify -Pintegration
# with vagrant cluster
mvn verify -Pintegration -Dtest.cluster=vagrant
# with vsphere cluster
mvn verify -Pintegration -Dtest.cluster=vsphere
```

### Skip assembly plugin execution

To skip ```maven-assembly-plugin``` during integration tests development you can:

```
mvn verify -Pintegration -Dassembly.skipAssembly
```

Thanks to that you can shorten development cycle of integration tests, when slider package does not change.

### Debugging

```
mvn verify -Pintegration -Dmaven.failsafe.debug
```
