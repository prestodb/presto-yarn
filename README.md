# Pre-requisities

* A cluster with HDP 2.2 or CDH5 installed
* JDK 1.8 
* Zookeeper 
* openssl >= 1.0.1e-16

# Create Slider App Package for Presto

## Building the project

Run ```mvn clean package``` and the presto app package will be packaged at presto-yarn-package/target/presto-yarn-package-1.0.0-SNAPSHOT.zip.

This .zip will have presto-server-<version>.tar.gz from Presto under package/files/. The Presto installed will use the configuration templates under package/templates.

## Preparing other slider specific configuration

* Copy the presto-yarn-package/src/test/resources/appConfig.json and presto-yarn-package/src/test/resources/resources-[singlenode|multinode].json to appConfig.json and resources.json respectively. Update the sample .json files with whatever configurations you want to have for Presto. If you are ok with the default values in the sample file you can  just use them too.
* If site.global.singlenode property in appConfig.json is set to true the master node will be set to run both coordinator and worker (singlenode mode). For multi-node set up, site.global.singlenode in appConfig.json should be set to false. The multinode resources-multinode-sample.json sample file is configured for a 4 node cluster where there will be 1 coordinator and 3 workers with strict placement policy, meaning, there will be one component instance running on every node irrespective of failure history.
* Make jdk8 the default java or add it to "java_home" in your appConfig.json
* The data directory (added in appConfig.json eg: /var/presto/) should be pre-created on all nodes and owned by user yarn, otherwise slider will fail to start Presto with permission errors.
* To configure the connectors with Presto add the following property in your appConfig.json. It should be of the format {'connector1' : ['key1=value1', 'key2=value2'..], 'connector2' : ['key1=value1', 'key2=value2'..]..}. This will create files connector1.properties, connector2.properties for Presto with entries key1=value1 etc.

```
    "site.global.catalog": "{'hive': ['connector.name=hive-cdh5', 'hive.metastore.uri=thrift://${NN_HOST}:9083'], 'tpch': ['connector.name=tpch']}"
```

### Using YARN label

To guarantee that a certain set of nodes are reserved for deploying Presto we can make use of YARN label expressions.

* First assign the nodes/subset of nodes with appropriate labels. See http://docs.hortonworks.com/HDPDocuments/HDP2/HDP-2.3.0/bk_yarn_resource_mgt/content/ch_node_labels.html
* Then set the components in resource.json with yarn.label.expression to have labels to be used when allocating containers for Presto.
* Create the application using bin/slider create .. --queue <queuename>. queuename will be queue defined in step one for the appropriate label.

If a label expression is specified for the slider-appmaster component then it also becomes the default label expression for all component. Sample resources.json may look like:

```
    "COORDINATOR": {
      "yarn.role.priority": "1",
      "yarn.component.instances": "1",
      "yarn.component.placement.policy": "1",
      "yarn.label.expression":"coordinator"
    },
    "WORKER": {
      "yarn.role.priority": "2",
      "yarn.component.instances": "2",
      "yarn.component.placement.policy": "1",
      "yarn.label.expression":"worker"
    }
```

where coordinator and worker are the node labels created and configured with a scheduler queue in YARN

### Configuring memory and CPU

``Memory``

yarn.memory in resources.json declares the amount of memory to ask for in YARN containers. It should be defined for each component, COORDINATOR and WORKER based on the expected memory consumption, measured in  MB. A YARN cluster is usually configured with a minimum container allocation, set in yarn-site.xml by the configuration parameter yarn.scheduler.minimum-allocation-mb. It will also have a maximum size set in yarn.scheduler.maximum-allocation-mb. Asking for more than this will result in the request being rejected.

The presto_jvm_heapsize property defined in appConfig.json, is used by the Presto JVM itself. Slider suggests that the value of yarn.memory must be bigger than this heapsize. The value of yarn.memory MUST be bigger than the heap size allocated to any JVM and Slider suggests using atleast 50% more appears to work, though some experimentation will be needed.

In addition, set other memory specific properties ```presto_query_max_memory``` and ```presto_query_max_memory_per_node``` in appConfig.json as you would set the properties ```query.max-memory``` and ```query.max-memory-per-node``` in Presto's config.properties.

``CPU``

Slider also supports configuring the virtual cores to use for the process which can be defined per component. yarn.vcores declares the number of "virtual cores" to request. Ask for more vcores if your process needs more CPU time.

TODO: Investigate CGroups in YARN

See http://slider.incubator.apache.org/docs/configuration/resources.html#core for more details.

### Failure policy

Yarn containers hosting Presto may fail due to some misconfiguration in Presto or some other conflicts. The number of times the component may fail within a failure window is defined in resources.json.

The related properties are:

* The duration of a failure window, a time period in which failures are counted. The related properties are yarn.container.failure.window.days, yarn.container.failure.window.hours, yarn.container.failure.window.minutes and should be set in the global section as it relates just to slider. The default value is yarn.container.failure.window.hours=6. The initial window is measured from the start of the slider application master —once the duration of that window is exceeded, all failure counts are reset, and the window begins again.
* The maximum number of failures of any component in this time period. yarn.container.failure.threshold is the property for this and in most cases, should be set proportional to the the number of instances of the component. For Presto clusters, where there will be one coordinator and some number of workers it is reasonable to have a failure threshold for workers more than that of coordinator. This is because a higher failure rate of worker nodes is to be expected if the cause of the failure is due to the underlying hardware. At the same time the threshold should be low enough to detect any Presto configuration issues causing the workers to fail rapidly and breach the threshold sooner.

These failure thresholds are all heuristics. When initially configuring an application instance, low thresholds reduce the disruption caused by components which are frequently failing due to configuration problems. In a production application, large failure thresholds and/or shorter windows ensures that the application is resilient to transient failures of the underlying YARN cluster and hardware.

Based on the placement policy there are two more failure related properties you can set.

* The configuration property yarn.node.failure.threshold defines how "unreliable" a node must be before it is skipped for placement requests.  This is only used for the default yarn.component.placement.policy where unreliable nodes are avoided.
* yarn.placement.escalate.seconds is the timeout after which slider will escalate the request of pending containers to be launched on other nodes. For strict placement policy where the requested components are deployed on all nodes, this property is irrelevant. For other placement policies this property is relevant and the higher the cost of migrating a component instance from one host to another, the longer value of escalation timeout is recommended. Thus slider will wait longer before the component instance is escalated to be started on other nodes. During restart, for cases where redeploying the component instances on the same node as before is beneficial (due to locality of data or similar reasons), a higher escalation timeout is recommended.

Take a look here: http://slider.incubator.apache.org/docs/configuration/resources.html#failurepolicy for more details on failure policy.

The app package built should look something like:

```
 unzip -l "$@" ../presto-yarn-package-1.0.0-SNAPSHOT.zip
Archive:  ../presto-yarn-package-1.0.0-SNAPSHOT.zip
  Length      Date    Time    Name
---------  ---------- -----   ----
     2020  08-14-2015 12:43   metainfo.xml
        0  08-14-2015 12:47   package/
        0  08-14-2015 15:40   package/templates/
      231  08-14-2015 12:43   package/templates/config.properties-COORDINATOR.j2
       69  08-14-2015 12:43   package/templates/node.properties.j2
      173  08-14-2015 12:43   package/templates/jvm.config.j2
      164  08-14-2015 15:40   package/templates/config.properties-WORKER.j2
        0  08-14-2015 15:39   package/scripts/
     2032  08-14-2015 12:43   package/scripts/configure.py
     1940  08-14-2015 12:43   package/scripts/prestoserver.py
     1575  08-14-2015 15:39   package/scripts/params.py
      891  08-14-2015 12:43   package/scripts/presto_worker.py
      787  08-14-2015 12:43   package/scripts/__init__.py
      896  08-14-2015 12:43   package/scripts/presto_coordinator.py
        0  08-14-2015 12:47   package/files/
404244891  08-14-2015 12:47   package/files/presto-server-<version>.tar.gz
      948  08-14-2015 12:43   package/files/README.txt
---------                     -------
404257959                     17 files
```

# Set up Slider on your cluster

* Download the slider installation file from http://slider.incubator.apache.org/index.html to one of your nodes in the cluster
```
tar -xvf slider-0.80.0-incubating-all.tar.gz
```
 
* Now configure Slider with JAVA_HOME and HADOOP_CONF_DIR in slider-0.80.0-incubating/conf/slider-env.sh
```
export JAVA_HOME=/usr/lib/jvm/java
export HADOOP_CONF_DIR=/etc/hadoop/conf
```
 
* Make sure the user running slider has a home dir in HDFS. I used yarn user, so did:
```
su hdfs
$ hdfs dfs -mkdir /user/<user>
$ hdfs dfs -chown <user>:<user> -R /user/<user>
```

* Configure zookeeper in conf/slider-client.xml. In case zookeper is listening on master:2181 you need to add there the following section:

```
  <property>
      <name>slider.zookeeper.quorum</name>
      <value>master:2181</value>
  </property>
```
 
* Now run slider as <user>
```
su <user>
cd slider-0.80.0-incubating
bin/slider package --install --name PRESTO --package ../presto-yarn-package-1.0.0-SNAPSHOT.zip
bin/slider create presto1 --template appConfig.json --resources resources.json (using modified .json files as per your requirement)
```

This should start your application, and you can see it under the Yarn RM webUI.

# Check the status of running application

If you want to check the status of running application you run the following

```
bin/slider status presto1
```

# Destroy the app and re-create

If you want to re-create the app due to some failures/re-configuration

```
bin/slider destroy presto1
bin/slider create presto1 --template appConfig.json --resources resources.json
```

# Links

https://bdch-wiki.td.teradata.com:8443/display/SWARM/Slider+Installation+and+Configuration+Tips
https://bdch-wiki.td.teradata.com:8443/display/SWARM/Sample+Slider+configuration+for+Presto (See header 'Presto over Slider (Prototype Testing in vsphere)' for the tested version of app-package).
http://slider.incubator.apache.org/docs/getting_started.html
