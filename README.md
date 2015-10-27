# Pre-requisities

* A cluster with HDP 2.2+ or CDH5.4+ installed
* JDK 1.8 
* Zookeeper 
* openssl >= 1.0.1e-16

# Create Slider App Package for Presto

## Building the project

Run ```mvn clean package``` and the presto app package will be packaged at presto-yarn-package/target/presto-yarn-package-1.0.0-SNAPSHOT.zip.

This .zip will have presto-server-<version>.tar.gz from Presto under package/files/. The Presto installed will use the configuration templates under package/templates.

The app package built should look something like:

```
 unzip -l "$@" ../presto-yarn-package-1.0.0-SNAPSHOT.zip
Archive:  ../presto-yarn-package-1.0.0-SNAPSHOT.zip
  Length      Date    Time    Name
---------  ---------- -----   ----
        0  2015-09-14 12:09   package/
        0  2015-09-14 12:09   package/files/
402847868  2015-09-14 12:09   package/files/presto-server-<version>.tar.gz
      889  2015-09-14 12:09   appConfig-default.json
      606  2015-09-14 12:09   resources-default.json
        0  2015-09-14 12:09   package/scripts/
        0  2015-09-14 12:09   package/templates/
      897  2015-09-14 12:09   package/scripts/presto_coordinator.py
      892  2015-09-14 12:09   package/scripts/presto_worker.py
     2205  2015-09-14 12:09   package/scripts/configure.py
      787  2015-09-14 12:09   package/scripts/__init__.py
     2033  2015-09-14 12:09   package/scripts/params.py
     1944  2015-09-14 12:09   package/scripts/presto_server.py
      948  2015-09-14 12:09   package/files/README.txt
      200  2015-09-14 12:09   package/templates/config.properties-WORKER.j2
       69  2015-09-14 12:09   package/templates/node.properties.j2
      268  2015-09-14 12:09   package/templates/config.properties-COORDINATOR.j2
      186  2015-09-14 12:09   package/templates/jvm.config.j2
     2020  2015-09-14 12:09   metainfo.xml
---------                     -------
402861812                     19 files
```

## Preparing other slider specific configuration

* Copy the presto-yarn-package/src/main/resources/appConfig.json and presto-yarn-package/src/main/resources/resources-[singlenode|multinode].json to appConfig.json and resources.json respectively. Update the sample .json files with whatever configurations you want to have for Presto. If you are ok with the default values in the sample file you can  just use them too.
* If site.global.singlenode property in appConfig.json is set to true the master node will be set to run both coordinator and worker (singlenode mode). For multi-node set up, site.global.singlenode in appConfig.json should be set to false. The multinode resources-multinode-sample.json sample file is configured for a 4 node cluster where there will be 1 coordinator and 3 workers with strict placement policy, meaning, there will be one component instance running on every node irrespective of failure history.
* Make jdk8 the default java or add it to "java_home" in your appConfig.json
* The data directory (added in appConfig.json eg: /var/presto/) should be pre-created on all nodes and owned by user yarn, otherwise slider will fail to start Presto with permission errors.
* To configure the connectors with Presto add the following property in your appConfig.json. It should be of the format {'connector1' : ['key1=value1', 'key2=value2'..], 'connector2' : ['key1=value1', 'key2=value2'..]..}. This will create files connector1.properties, connector2.properties for Presto with entries key1=value1 etc.

```
    "site.global.catalog": "{'hive': ['connector.name=hive-cdh5', 'hive.metastore.uri=thrift://${NN_HOST}:9083'], 'tpch': ['connector.name=tpch']}"
```

* If you want to use a port other than 8080 for Presto server, configure it via site.global.presto_server_port in appConfig.json

* HDFS home directory created for user yarn ```/user/yarn``` with ```yarn``` user as an owner
  
  ```
    hadoop fs -mkdir -p /user/yarn
    hadoop fs -chown yarn:yarn /user/yarn
  ```

### Configuring memory and CPU

Memory and CPU related configuration properties must be modified as per your cluster configuration and requirements.

``Memory``

yarn.memory in resources.json declares the amount of memory to ask for in YARN containers. It should be defined for each component, COORDINATOR and WORKER based on the expected memory consumption, measured in  MB. A YARN cluster is usually configured with a minimum container allocation, set in yarn-site.xml by the configuration parameter yarn.scheduler.minimum-allocation-mb. It will also have a maximum size set in yarn.scheduler.maximum-allocation-mb. Asking for more than this will result in the request being rejected.

The presto_jvm_heapsize property defined in appConfig.json, is used by the Presto JVM itself. Slider suggests that the value of yarn.memory must be bigger than this heapsize. The value of yarn.memory MUST be bigger than the heap size allocated to any JVM and Slider suggests using atleast 50% more appears to work, though some experimentation will be needed.

In addition, set other memory specific properties ```presto_query_max_memory``` and ```presto_query_max_memory_per_node``` in appConfig.json as you would set the properties ```query.max-memory``` and ```query.max-memory-per-node``` in Presto's config.properties.

``CPU``

Slider also supports configuring the YARN virtual cores to use for the process which can be defined per component. yarn.vcores declares the number of "virtual cores" to request. Ask for more vcores if your process needs more CPU time.

See http://slider.incubator.apache.org/docs/configuration/resources.html#core for more details.

``CGroups in YARN``

If you are using CPU scheduling (using the DominantResourceCalculator), you should also use CGroups to constrain and manage CPU processes. CGroups compliments CPU scheduling by providing CPU resource isolation. With CGroups strict enforcement turned on, each CPU process gets only the resources it asks for. This way, we can guarantee that containers hosting Presto services is assigned with a percentage of CPU. If you have another process that needs to run on a node that also requires CPU resources, you can lower the percentage of CPU allocated to YARN to free up resources for the other process.

See Hadoop documentation on how to configure CGroups in YARN: https://hadoop.apache.org/docs/current/hadoop-yarn/hadoop-yarn-site/NodeManagerCgroups.html. Once you have CGroups configured, Presto on YARN containers will be configured in the CGroups hierarchy like any other YARN application containers.

Slider can also define YARN queues to submit the application creation request to, which can set the priority, resource limits and other values of the application. But this configuration is global to Slider and defined in conf/slider-client.xml. You can define the queue name and also the priority within the queue. All containers created in the Slider cluster will share this same queue.

```
    <property>
      <name>slider.yarn.queue</name>
      <value>default</value>
    </property>

    <property>
      <name>slider.yarn.queue.priority</name>
      <value>1</value>
    </property>
```

### Failure policy

Follow this section if you want to change the default Slider failure policy. Yarn containers hosting Presto may fail due to some misconfiguration in Presto or some other conflicts. The number of times the component may fail within a failure window is defined in resources.json.

The related properties are:

* The duration of a failure window, a time period in which failures are counted. The related properties are yarn.container.failure.window.days, yarn.container.failure.window.hours, yarn.container.failure.window.minutes and should be set in the global section as it relates just to slider. The default value is yarn.container.failure.window.hours=6. The initial window is measured from the start of the slider application master —once the duration of that window is exceeded, all failure counts are reset, and the window begins again.
* The maximum number of failures of any component in this time period. yarn.container.failure.threshold is the property for this and in most cases, should be set proportional to the the number of instances of the component. For Presto clusters, where there will be one coordinator and some number of workers it is reasonable to have a failure threshold for workers more than that of coordinator. This is because a higher failure rate of worker nodes is to be expected if the cause of the failure is due to the underlying hardware. At the same time the threshold should be low enough to detect any Presto configuration issues causing the workers to fail rapidly and breach the threshold sooner.

These failure thresholds are all heuristics. When initially configuring an application instance, low thresholds reduce the disruption caused by components which are frequently failing due to configuration problems. In a production application, large failure thresholds and/or shorter windows ensures that the application is resilient to transient failures of the underlying YARN cluster and hardware.

Based on the placement policy there are two more failure related properties you can set.

* The configuration property yarn.node.failure.threshold defines how "unreliable" a node must be before it is skipped for placement requests.  This is only used for the default yarn.component.placement.policy where unreliable nodes are avoided.
* yarn.placement.escalate.seconds is the timeout after which slider will escalate the request of pending containers to be launched on other nodes. For strict placement policy where the requested components are deployed on all nodes, this property is irrelevant. For other placement policies this property is relevant and the higher the cost of migrating a component instance from one host to another, the longer value of escalation timeout is recommended. Thus slider will wait longer before the component instance is escalated to be started on other nodes. During restart, for cases where redeploying the component instances on the same node as before is beneficial (due to locality of data or similar reasons), a higher escalation timeout is recommended.

Take a look here: http://slider.incubator.apache.org/docs/configuration/resources.html#failurepolicy for more details on failure policy.


### Using YARN label

This is an optional feature and is not required to run Presto in YARN. To guarantee that a certain set of nodes are reserved for deploying Presto or to configure a particular node for a component type we can make use of YARN label expressions.

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

# 'Flex'ible app

Flex the number of Presto workers to the new value. If greater than before, new copies of the  worker will be requested. If less, component instances will be destroyed.

Changes are not immediate and depend on the availability of resources in the YARN cluster. Make sure while flex that there is an extra free node(if adding) with YARN nodemanagers running are available. Also make sure these nodes do not have existing presto component. Otherwise Slider will add Presto workers to the existing node where Presto might already be running and deploying the newly 'flex'ed worker component will fail.

eg: Asumme there are 2 nodes (with YARN nodemanagers running) in the cluster and you initially deployed only one of the nodes with Presto via Slider. If you want to deploy and start Presto WORKER component on the second node (assuming it meets all resource requirements) and thus have the total number of WORKERS to be 2, then run:

```
bin/slider flex presto1 --component WORKER 2
```

Please note that if your cluster already had 3 WORKER nodes running, the above command will destroy one of them and retain 2 WORKERs.

# Debugging and Logging

It is recommended that log aggregation of YARN application log files be enabled in YARN, using yarn.log-aggregation-enable property in your yarn-site.xml. Then slider logs created during the launch of Presto-YARN will be available locally on your nodemanager nodes under contanier logs directory eg: /var/log/hadoop-yarn/application_<id>/container_<id>/. For any retries attempted by Slider to launch Presto a new container will be launched and hence you will find a new container_<id> directory.  You can look for any errors under errors_*.txt there, and also there is a slider-agent.log file which will give you Slider application lifetime details.

Subsequently every Slider application owner has the flexibility to set the include and exclude patterns of file names that they intend to aggregate, by adding the following properties in their resources.json. For example, using
```
 "global": {
    "yarn.log.include.patterns": "*",
    "yarn.log.exclude.patterns": "*.*out"
  }
```

See http://slider.incubator.apache.org/docs/configuration/resources.html#logagg for details.

Presto logs will be available under the standard Presto data directory location. By default it is /var/presto/data/var/log directory where /var/presto/data is the default data directory configured in Slider appConfig.json. You can find both server.log and http-request.log files here. Please note that log rotation of these Presto log files will have to be manually enabled (for eg: using http://linuxcommand.org/man_pages/logrotate8.html)

# Ambari Integration


You can also deploy Presto in Yarn via Ambari. Ambari provides Slider integration and also supports deploying any Slider application package using Slider 'views'. Slider View for Ambari delivers an integrated experience for deploying and managing Slider apps from Ambari Web.

The steps for deploying Presto on Yarn via Slider views in Ambari are:

* Install Ambari server. You may refer: http://docs.hortonworks.com/HDPDocuments/Ambari-2.1.0.0/bk_Installing_HDP_AMB/content/ch_Installing_Ambari.html.

* Copy the app package ```presto-yarn-package-1.0.0-SNAPSHOT.zip``` to ```/var/lib/resources/ambari-server/apps/``` directory on your Ambari server node. Restart ambari-server.

* Prepare hdfs for Slider
  
  ```
su hdfs
hdfs dfs -mkdir /user/yarn
hdfs dfs -chown yarn:hdfs /user/yarn
```

* Now Log In to Apache Ambari, ```http://ambariserver_ip:8080``` #username-admin password-admin
   
* Name your cluster, provide the configuration of the cluster and follow the steps on the WebUI.

* Customize/configure the services and install them. A minimum of HDFS, YARN, Zookeeper is required for Slider to work. You must also also select Slider to be installed.

* Once you have all the services up and running on the cluster, you can configure Slider in Ambari to manage your application by going to "Views" at http://ambariserver_ip:8080/views/ADMIN_VIEW/2.0.0/INSTANCE/#/. There, create a Slider View by populating all the necessary fields with a preferred instance name (eg: Slider).

* Select the "Views" control icon in the upper right, select the instance you created in the previous step, eg: "Slider".

* Provide details of the Presto service. By default the UI will be populated with the values you have in the ```*-default.json``` files in your ```presto-yarn-package-1.0.0-SNAPSHOT.zip```.

* Click Finish. This will basically do the equivalent of ```package  --install``` and ```create``` you do via the bin/slider script. Once successfully deployed, you will see the Yarn application started for Presto.

* You can manage the application lifecycle (e.g. start, stop, flex, destroy) from the View UI.

# Links

 * http://slider.incubator.apache.org/docs/getting_started.html
 * http://docs.hortonworks.com/HDPDocuments/Ambari-2.0.1.0/bk_Installing_HDP_AMB/content/ch_Installing_Ambari.html
