# Presto slider package - product tests


## Pre-requisites

In order to run product tests you need to have: 

 * a provisioned cluster which they will be run (e.g. an hfab vagrant or vsphere cluster) with java 8 preinstalled on the cluster
 
```
hfab vagrant.provision && hfab vagrant hortonworks.install:java_version=8,version=2.2
# or
hfab vsphere.provision && hfab vsphere hortonworks.install:java_version=8,version=2.2
```

 * all the nodes of cluster have to be accessible from your local machine, so you can update your /etc/hosts file with entities returned by the command

```
 hfab vagrant etc_hosts
 # or
 hfab vsphere etc_hosts
```

 * make sure that network locations (like a property ```yarn.resourcemanager.address, yarn.resourcemanager.scheduler.address, slider.zookeeper.quorum```) from ```src/test/resources/slider/conf/slider-client.xml``` file are accessible from your local machine

 * set a password for yarn user on master node. Create a ```src/test/resources/tempto-configuration-local.yaml``` yarn@master password and a private key to connect any cluster node as root user, example settings:

```
ssh:
  identity: /home/kogut/teradata/hfab/hfab/util/pkg_data/insecure_key.pem
  roles:
    yarn:
      password: yarn
```

## Execution

To run product tests you need to enable maven profile ```productTests``` and then all product tests will be executed run during ```mvn test``` phase. To successfully build, you should first get tempto and build it successfully. Clone https://github.com/prestodb/tempto and build it using ```./gradlew install -x signArchives```.

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

## Troubleshooting 

 * In case you run a lot tests in row it is possible that hdfs usercache is getting so large causing yarn node go into state UNHEALTHY, to fix that run:

```
for node in master slave{1,2,3}; do
 ssh $node 'rm -rf  /mnt/hadoop-hdfs/nm-local-dir/*cache*'
 ssh $node /etc/init.d/hadoop-yarn-nodemanager restart
done
```
