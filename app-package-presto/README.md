# Presto slider package

##Integration tests

### Pre-requisites

In order to run integration you need to have: 
 * a cluster provisioned on which they will be run (e.g. a hfab vagrant cluster)
 * with java 8 preinstalled on the cluster
```
hfab vagrant.provision && hfab vagrant cloudera.install:java_version=8
```
 * yarn resource manager accessible at master:8050 from your sandbox host (default vagrant configuration)
 * zookeeper configured in a way it is accessible on master:55555181 from your sandbox host (default vagrant configuration)
 * ```/var/presto``` directory created on all the nodes with ```yarn``` user as an owner
 * HDFS home directory created for user yarn ```/user/yarn``` with ```yarn``` user as an owner

Note: vagrant cluster comes with old version of openssl library, please make sure you upgraded it before testing.
 

### Execution

To run integration tests you need to enable maven profile ```integration``` and then all integration tests will be run during ```mvn verify``` phase.

```
mvn verify -Pintegration
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
