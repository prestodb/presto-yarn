# Pre-requisities

* A cluster with HDP 2.2+ or CDH5.4+ installed
* Apache Slider 0.80.0
* JDK 1.8 
* Zookeeper 
* openssl >= 1.0.1e-16

# Create Presto App Package

First step is to build the presto-yarn-package-*.zip package to deploy Presto on YARN.

## Building the project

Run ```mvn clean package``` and the presto app package will be packaged at ``presto-yarn-package/target/presto-yarn-package-*.zip``.

This .zip will have presto-server-*.tar.gz from Presto under ``package/files/``. The Presto installed will use the configuration templates under ``package/templates``.

The app package built should look something like:

```
 unzip -l "$@" ../presto-yarn-package-1.0.0-SNAPSHOT.zip
Archive:  ../presto-yarn-package-1.0.0-SNAPSHOT.zip
  Length      Date    Time    Name
---------  ---------- -----   ----
        0  2015-11-30 22:57   package/
        0  2015-11-30 22:57   package/files/
411459833  2015-11-30 20:26   package/files/presto-server-0.123.tar.gz
     1210  2015-11-30 22:57   appConfig-default.json
      606  2015-11-30 22:57   resources-default.json
        0  2015-11-30 20:26   package/scripts/
        0  2015-11-30 21:22   package/plugins/
        0  2015-11-30 20:26   package/templates/
      897  2015-11-30 22:57   package/scripts/presto_coordinator.py
      892  2015-11-30 22:57   package/scripts/presto_worker.py
     2801  2015-11-30 22:57   package/scripts/configure.py
      787  2015-11-30 22:57   package/scripts/__init__.py
     2285  2015-11-30 22:57   package/scripts/params.py
     1944  2015-11-30 22:57   package/scripts/presto_server.py
       35  2015-11-30 22:57   package/plugins/README.txt
      948  2015-11-30 22:57   package/files/README.txt
      236  2015-11-30 22:57   package/templates/config.properties-WORKER.j2
       69  2015-11-30 22:57   package/templates/node.properties.j2
      304  2015-11-30 22:57   package/templates/config.properties-COORDINATOR.j2
     2020  2015-11-30 22:57   metainfo.xml
---------                     -------
411474867                     20 files
```

# Presto-YARN deployment 

Presto on YARN can be set up either manually using Apache Slider or via Ambari Slider Views if you are planning to use HDP distribution.

## Presto App Package configuration

There are some sample configuration options files available at ``presto-yarn-package/src/main/resources`` directory in the repository. ``appConfig.json`` and ``resources-[singlenode|mutlinode].json`` files are the two major configuration files you need to configure before you can get Presto running on YARN. Copy the ``presto-yarn-package/src/main/resources/``appConfig.json```` and ``presto-yarn-package/src/main/resources/resources-[singlenode|multinode].json`` to a local file at a location where you are planning to run Slider. Name them as ``appConfig.json`` and ``resources.json``. Update these sample json files with whatever configurations you want to have for Presto. If you are ok with the default values in the sample file you can just use them as-is.

``Note``: If you are planning to use Ambari for your installation skip to the section ``Installation Using Ambari Slider View`` here. Changing these files manually is needed only if you are going to install Presto on YARN manually using Slider. If installing via Ambari, you can change these configurations from the Slider view.

Follow the steps here and configure the presto-yarn configuration files to match your cluster requirements.

### appConfig.json

* If ``site.global.singlenode`` property in appConfig.json is set to true the master node will be set to run both coordinator and worker (singlenode mode). For multi-node set up, ``site.global.singlenode`` in appConfig.json should be set to false.
* Presto requires Java 1.8. So make jdk8 the default java or add it to "java_home" in your ``appConfig.json``
* The data directory (added in ``appConfig.json`` eg: ``/var/presto/``) should be pre-created on all nodes and must be owned by user ``yarn``, otherwise slider will fail to start Presto with permission errors.
* Presto's ``jvm.config`` must be configured using ``site.global.jvm_args`` in ``appConfig.json``. Since Presto needs the ``jvm.config`` format to be a list of options, one per line, this property must be a String representation of list of strings. Each entry of this list will be a new line in your jvm.config. For example the configuration should look like:
```
    "site.global.jvm_args": "['-server', '-Xmx1024M', '-XX:+UseG1GC', '-XX:G1HeapRegionSize=32M', '-XX:+UseGCOverheadLimit', '-XX:+ExplicitGCInvokesConcurrent', '-XX:+HeapDumpOnOutOfMemoryError', '-XX:OnOutOfMemoryError=kill -9 %p']",
```

* To configure the connectors with Presto add the following property in your ``appConfig.json``. It should be of the format (note the single quotes around each value) - {'connector1' : ['key1=value1', 'key2=value2'..], 'connector2' : ['key1=value1', 'key2=value2'..]..}. This will create files connector1.properties, connector2.properties for Presto with entries key1=value1 etc.

```
    "site.global.catalog": "{'hive': ['connector.name=hive-cdh5', 'hive.metastore.uri=thrift://${NN_HOST}:9083'], 'tpch': ['connector.name=tpch']}"
```

``Note``: The NN_HOST used in ``hive.metastore.uri`` is a variable for your HDFS Namenode and this expects that your hive metastore is up and running on your Namenode host. You do not have to replace that with your actual Namenode hostname. This variable will be substituted with your Namenode hostname during runtime. If you have hive metastore running elsewhere make sure you update NN_HOST with the appropriate hostname.

* To add plugin jars add the ``site.global.plugin`` property in your ``appConfig.json``. It should be of the format {'connector1' : ['jar1', 'jar2'..], 'connector2' : ['jar3', 'jar4'..]..}. This will copy jar1, jar2 to Presto plugin directory at plugin/connector1 directory and jar3, jar4 at plugin/connector2 directory. Make sure you have the plugin jars you want to add to Presto available at ```presto-yarn-package/src/main/slider/package/plugins/``` prior to building the presto-yarn app package and thus the app package built presto-yarn-package-*.zip will have the jars under ```package/plugins``` directory.

* If you want to use a port other than 8080 for Presto server, configure it via ``site.global.presto_server_port`` in ``appConfig.json``

* Variables in ``appConfig.json`` like ``${COORDINATOR_HOST}``, ``${AGENT_WORK_ROOT}`` etc. do not need any substitution and will be appropriately configured during runtime.

### resources.json

* The multinode ``presto-yarn-package/src/main/resources/rresources-multinode.json`` sample file is now configured for a 4 node cluster where there will be 1 coordinator and 3 workers with strict placement policy, meaning, there will be one component instance running on every node irrespective of failure history. If there are insufficient number of nodemanager nodes in your cluster to accomodate the number of workers requested, the application launch will fail.

### Other configuration

* HDFS home directory created for the app_user. This has to be the same as ``site.global.app_user`` set in ``appConfig.json``. Eg: for user ``yarn`` create ```/user/yarn``` with ```yarn``` user as an owner
  
  ```
    hdfs dfs -mkdir -p /user/yarn
    hdfs dfs -chown yarn:yarn /user/yarn
  ```
``Note``: For operations involving Hive connector in Presto, especially INSERT, ALTER TABLE etc, it may require that the user running Presto has access to HDFS directories like Hive warehouse directories. So make sure that the app_user you set has appropriate access permissions to those HDFS directories.
For eg: ``/apps/hive/warehouse`` is usually where Presto user will need access for various DML operations involving Hive connector and is owned by ``hdfs`` in most cases. In that case, one way to fix the permission issue is to set ``site.global.app_user`` to user ``hdfs`` and also create ``/user/hdfs`` directory in HDFS if not already there (as above). You will also need to  run any slider scripts(bin/slider) as user ``hdfs`` in this case.

Now you are ready to deploy Presto on YARN either manually or via Ambari.

## Manual Installation via Slider

* Download the slider 0.80.0 installation file from http://slider.incubator.apache.org/index.html to one of your nodes in the cluster
```
tar -xvf slider-0.80.0-incubating-all.tar.gz
```
 
* Now configure Slider with JAVA_HOME and HADOOP_CONF_DIR in ``slider-0.80.0-incubating/conf/slider-env.sh``
```
export JAVA_HOME=/usr/lib/jvm/java
export HADOOP_CONF_DIR=/etc/hadoop/conf
```
 
* Configure zookeeper in ``conf/slider-client.xml``. In case zookeper is listening on master:2181 you need to add there the following section:

```
  <property>
      <name>slider.zookeeper.quorum</name>
      <value>master:2181</value>
  </property>
```
 
* Configure path where slide packages will be installed

``` 
  <property>
    <name>fs.defaultFS</name>
    <value>hdfs://master/</value>
  </property>
```
 
* Make sure the user running slider, which should be same as ``site.global.app_user`` in ``appConfig.json``, has a home dir in HDFS (See note under ``Other configuration`` section above to decide the user you want to choose).
```
su hdfs
$ hdfs dfs -mkdir /user/<user>
$ hdfs dfs -chown <user>:<user> -R /user/<user>
```

* Now run slider as <user>

For more details on ``appConfig.json`` and ``resources.json`` follow ``Advanced Configuration`` section below. One thing you need to make sure is that the ``site.global.data_dir`` you set in ``appConfig.json`` should be created on all nodes and owned by user ``yarn``.

```
su <user>
cd slider-0.80.0-incubating
bin/slider package --install --name PRESTO --package ../presto-yarn-package-*.zip
bin/slider create presto1 --template appConfig.json --resources resources.json (using modified .json files as per your requirement)
```

This should start your application, and you can see it under the Yarn RM webUI.

### Additional Slider commands

Some additional slider commands to manage your existing Presto application.

#### Check the status of running application

If you want to check the status of running application you run the following

```
bin/slider status presto1
```

#### Destroy the app and re-create

If you want to re-create the app due to some failures or you want to reconfigure Presto (eg: add a new connector)

```
bin/slider destroy presto1
bin/slider create presto1 --template appConfig.json --resources resources.json
```

#### 'Flex'ible app

Flex the number of Presto workers to the new value. If greater than before, new copies of the  worker will be requested. If less, component instances will be destroyed.

Changes are not immediate and depend on the availability of resources in the YARN cluster. Make sure while flex that there is an extra free node(if adding) with YARN nodemanagers running are available. Also make sure these nodes do not have existing presto component. Otherwise Slider will add Presto workers to the existing node where Presto might already be running and deploying the newly 'flex'ed worker component will fail.

eg: Asumme there are 2 nodes (with YARN nodemanagers running) in the cluster and you initially deployed only one of the nodes with Presto via Slider. If you want to deploy and start Presto WORKER component on the second node (assuming it meets all resource requirements) and thus have the total number of WORKERS to be 2, then run:

```
bin/slider flex presto1 --component WORKER 2
```

Please note that if your cluster already had 3 WORKER nodes running, the above command will destroy one of them and retain 2 WORKERs.


## Installation using Ambari Slider View

You can also deploy Presto in Yarn via Ambari. Ambari provides Slider integration and also supports deploying any Slider application package using Slider 'views'. Slider View for Ambari delivers an integrated experience for deploying and managing Slider apps from Ambari Web.

The steps for deploying Presto on Yarn via Slider views in Ambari are:

* Install Ambari server. You may refer: http://docs.hortonworks.com/HDPDocuments/Ambari-2.1.0.0/bk_Installing_HDP_AMB/content/ch_Installing_Ambari.html.

* Copy the app package ```presto-yarn-package-*.zip``` to ```/var/lib/ambari-server/resources/apps/``` directory on your Ambari server node. Restart ambari-server.

* Prepare HDFS for Slider. The user directory you create here should be for the same user you set in ``site.global.app_user`` configuration in the Slider View later from the Ambari UI. If the ``app_user`` is going to be ``yarn`` then do:
  
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

* Provide details of the Presto service. By default, the UI will be populated with the values you have in the ```*-default.json``` files in your ```presto-yarn-package-*.zip```.

* Make sure you change the global.presto_server_port from 8080 to some other unused port, since Ambari by default uses 8080.

* Make sure the data directory in the UI (added in appConfig-default.json eg: /var/presto/) is pre-created on all nodes and the directory must owned by user yarn, otherwise slider will fail to start Presto with permission errors.

* Click Finish. This will basically do the equivalent of ```package  --install``` and ```create``` you do via the bin/slider script. Once successfully deployed, you will see the Yarn application started for Presto.

* You can manage the application lifecycle (e.g. start, stop, flex, destroy) from the View UI.

### Reconfiguration in Slider View

Once the application is launched if you want to update the configuration of Presto (eg: add a new connector), first go to Actions on the Slider View instance screen and stop the running application.

Once the running YARN application is stopped, under Actions you will have an option to 'Destroy' the existing Presto instance running via Slider. Destroy the existing one and re-create a new instance with whatever updates you want to make to the configuration.

## Advanced Configuration

A little deeper explanation on various configuration options available.

### Configuring memory and CPU

Memory and CPU related configuration properties must be modified as per your cluster configuration and requirements.

``Memory``

yarn.memory in resources.json declares the amount of memory to ask for in YARN containers. It should be defined for each component, COORDINATOR and WORKER based on the expected memory consumption, measured in  MB. A YARN cluster is usually configured with a minimum container allocation, set in yarn-site.xml by the configuration parameter yarn.scheduler.minimum-allocation-mb. It will also have a maximum size set in yarn.scheduler.maximum-allocation-mb. Asking for more than this will result in the request being rejected.

The heapsize defined as -Xmx of jvm_args in ``appConfig.json``, is used by the Presto JVM itself. Slider suggests that the value of yarn.memory must be bigger than this heapsize. The value of yarn.memory MUST be bigger than the heap size allocated to any JVM and Slider suggests using atleast 50% more appears to work, though some experimentation will be needed.

In addition, set other memory specific properties ```presto_query_max_memory``` and ```presto_query_max_memory_per_node``` in ``appConfig.json`` as you would set the properties ```query.max-memory``` and ```query.max-memory-per-node``` in Presto's config.properties.

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

# Debugging and Logging

It is recommended that log aggregation of YARN application log files be enabled in YARN, using yarn.log-aggregation-enable property in your yarn-site.xml. Then slider logs created during the launch of Presto-YARN will be available locally on your nodemanager nodes under contanier logs directory eg: ``/var/log/hadoop-yarn/application_<id>/container_<id>/``. For any retries attempted by Slider to launch Presto a new container will be launched and hence you will find a new container_<id> directory.  You can look for any errors under errors_*.txt there, and also there is a slider-agent.log file which will give you Slider application lifetime details.

Subsequently every Slider application owner has the flexibility to set the include and exclude patterns of file names that they intend to aggregate, by adding the following properties in their resources.json. For example, using
```
 "global": {
    "yarn.log.include.patterns": "*",
    "yarn.log.exclude.patterns": "*.*out"
  }
```

See http://slider.incubator.apache.org/docs/configuration/resources.html#logagg for details.

Presto logs will be available under the standard Presto data directory location. By default it is ``/var/presto/data/var/log`` directory where ``/var/presto/data`` is the default data directory configured in Slider ``appConfig.json``. You can find both ``server.log`` and ``http-request.log`` files here. Please note that log rotation of these Presto log files will have to be manually enabled (for eg: using http://linuxcommand.org/man_pages/logrotate8.html)


# Links

 * http://slider.incubator.apache.org/docs/getting_started.html
 * http://docs.hortonworks.com/HDPDocuments/Ambari-2.0.1.0/bk_Installing_HDP_AMB/content/ch_Installing_Ambari.html
