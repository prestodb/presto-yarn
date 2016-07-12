Manual Installation via Slider
==============================

Note: Only slider-0.80.0 is officially supported, and the code has not been tested for other versions.

1. Download the slider 0.8* installation file from http://slider.incubator.apache.org/index.html to one of your nodes in the cluster

::
   
   tar -xvf slider-0.80.0-incubating-all.tar.gz

 
2. Now configure Slider with JAVA_HOME and HADOOP_CONF_DIR in ``slider-0.80.0-incubating/conf/slider-env.sh``

::
   
   export JAVA_HOME=/usr/lib/jvm/java
   export HADOOP_CONF_DIR=/etc/hadoop/conf

 
3. Configure zookeeper in ``conf/slider-client.xml``. In case zookeper is listening on ``master:2181`` you need to add there the following section: 

::
   
   <property>
      <name>slider.zookeeper.quorum</name>
      <value>master:2181</value>
   </property>

 
4. Configure path where slide packages will be installed

::
   
   <property>
     <name>fs.defaultFS</name>
     <value>hdfs://master/</value>
   </property>

 
5. Make sure the user running slider, which should be same as ``site.global.app_user`` in ``appConfig.json``, has a home dir in HDFS (See note [here](#appconfig)).

::
   
   su hdfs
   $ hdfs dfs -mkdir -p /user/<user>
   $ hdfs dfs -chown <user>:<user> -R /user/<user>


6. Now run slider as <user>. For more details on [appConfig.json](#appconfig) and [resources.json](#resources) follow  [configuration](#advanced) section.

::

   su <user>
   cd slider-0.80.0-incubating
   bin/slider package --install --name PRESTO --package ../presto-yarn-package-*.zip
   bin/slider create presto1 --template appConfig.json --resources resources.json (using modified .json files as per your requirement)


This should start your application, and you can see it under the Yarn ResourceManager webUI. If your application is successfully run, it should continuously be available in the YARN resource manager as a running application. If the job fails, please be sure to check the job history's logs along with the logs on the node's disk (more information [below](#debugging)).

Additional Slider commands
--------------------------

Some additional slider commands to manage your existing Presto application.

Check the status
~~~~~~~~~~~~~~~~

If you want to check the status of running application you run the following, and you will have status printed to a file ``status_file``

::

   bin/slider status presto1 --out status_file


Destroy the app and re-create
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

If you want to re-create the app due to some failures or you want to reconfigure Presto (eg: add a new connector)

::
   
   bin/slider destroy presto1
   bin/slider create presto1 --template appConfig.json --resources resources.json


Completely remove the app
~~~~~~~~~~~~~~~~~~~~~~~~~

::
   
   bin/slider package --delete --name PRESTO


'Flex'ible app
~~~~~~~~~~~~~~

Flex the number of Presto workers to the new value. If greater than before, new copies of the  worker will be requested. If less, component instances will be destroyed.

Changes are immediate and depend on the availability of resources in the YARN cluster. Make sure while flex that there are extra nodes available(if adding) with YARN nodemanagers running and also Presto data directory pre-created/owned by ``yarn`` user. Also make sure these nodes do not have a Presto component already running, which may cause flex-ing to deploy worker on these nodes and eventually failing.

eg: Asumme there are 2 nodes (with YARN nodemanagers running) in the cluster and you initially deployed only one of the nodes with Presto via Slider. If you want to deploy and start Presto WORKER component on the second node (assuming it meets all resource requirements) and thus have the total number of WORKERS to be 2, then run:

::
   
   bin/slider flex presto1 --component WORKER 2


Please note that if your cluster already had 3 WORKER nodes running, the above command will destroy one of them and retain 2 WORKERs.
