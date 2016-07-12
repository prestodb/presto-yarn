Automated Installation using Apache Ambari Slider View
======================================================

You can deploy Presto in Yarn via Ambari. Ambari provides Slider integration and also supports deploying any Slider application package using Slider 'views'. Slider View for Ambari delivers an integrated experience for deploying and managing Slider apps from Ambari Web.

The steps for deploying Presto on Yarn via Slider views in Ambari are:

#. Install Ambari server. You may refer:
   http://docs.hortonworks.com/HDPDocuments/Ambari-2.1.0.0/bk_Installing_HDP_AMB/content/ch_Installing_Ambari.html.

#. Copy the app package ```presto-yarn-package-<version>-<presto-version>.zip``` to ```/var/lib/ambari-server/resources/apps/``` directory on your Ambari server node. Restart ambari-server.

#. Now Log In to Apache Ambari, ```http://ambariserver_ip:8080``` #username-admin password-admin
   
#. Name your cluster, provide the configuration of the cluster and follow the steps on the WebUI.

#. Customize/configure the services and install them. A minimum of HDFS, YARN, Zookeeper is required for Slider to work. You must also also select Slider to be installed.

#. For the Slider client installed, you need to update the configuration if you are not using the default installation paths for Hadoop and Zookeeper. Thus ```slider-env.sh``` should point to your ```JAVA_HOME``` and ```HADOOP_CONF_DIR```
  
::
   
   export JAVA_HOME=/usr/lib/jvm/java
   export HADOOP_CONF_DIR=/etc/hadoop/conf


#. For zookeeper if you are using a different installation directory from the default one at ```/usr/lib/zookeeper``` add a custom property to ```slider-client``` section in Slider configuration with key: ```zk.home``` and value: ```path_to_your_zookeeper```. If using a different port from default one ```2181``` then add key: ```slider.zookeeper.quorum``` and value: ```master:5181``` where ```master``` is the node and ```5181``` is the port.
       
#. Once you have all the services up and running on the cluster, you can configure Slider in Ambari to manage your application by creating a "View". Go to ``admin`` (top right corner) -> ``Manage Ambari`` and then from the left pane select ``Views``.

#. There, create a Slider View by populating all the necessary fields with a preferred instance name (eg: Slider). ``ambari.server.url`` can be of the format - ``http://<ambari-server-url>:8080/api/v1/clusters/<clustername>``, where ``<clustername>`` is what you have named your Ambari cluster.

#. Select the "Views" control icon in the upper right, select the instance you created in the previous step, eg: "Slider".

#. Now click ``Create App`` to create a new Presto YARN application.

#. Provide details of the Presto service. By default, the UI will be populated with the values you have in the ```*-default.json``` files in your ```presto-yarn-package-*.zip```.

#. The app name should be of lower case, eg: presto1
 
#. You can set the configuration property fields as per your cluster requirement. For example, if you want to set a connector for Presto, you can update the ```global.catalog``` property. See [here](#packageconfig) for explanation on each configuration variable.

#. Prepare HDFS for Slider. The user directory you create here should be for the same user you set in ``global.app_user`` field. If the ``app_user`` is going to be ``yarn`` then do:
  
::
   
   su hdfs
   hdfs dfs -mkdir -p /user/yarn
   hdfs dfs -chown yarn:yarn /user/yarn


#. Make sure you change the ``global.presto_server_port`` from 8080 to some other unused port, since Ambari by default uses 8080.

#. Make sure the data directory in the UI (added in ``appConfig-default.json`` eg: ``/var/lib/presto/``) is pre-created on all nodes and the directory must be owned by ``global.app_user``, otherwise slider will fail to start Presto with permission errors.

#. If you want to add any additional Custom properties, use Custom property section. Additional properties supported as of now are ``site.global.plugin``, ``site.global.additional_config_properties`` and ``site.global.additional_node_properties``. See [section](#packageconfig) above for requirements and format of these properties.

#. Click Finish. This will basically do the equivalent of ```package  --install``` and ```create``` you do for [manual](#manual) installation. Once successfully deployed, you will see the Yarn application started for Presto. You can click on app launched, and then monitor the status either from Slider view or you can click on the ``Quick Links`` which should take you to the YARN WebUI. If your application is successfully run, it should continuously be available in the YARN resource manager as a "RUNNING" application.

#. If the job fails, please be sure to check the job history’s logs along with the logs on the node’s disk. Refer this [section](#debugging) for more details.

#. You can manage the application lifecycle (e.g. start, stop, flex, destroy) from the View UI.

Reconfiguration in Slider View
------------------------------

Once the application is launched if you want to update the configuration of Presto (eg: add a new connector), first go to ``Actions`` on the Slider View instance screen and stop the running application.

Once the running YARN application is stopped, under ``Actions`` you will have an option to ``Destroy`` the existing Presto instance running via Slider. ``Destroy`` the existing one and re-create a new app (``Create App`` button) with whatever updates you want to make to the configuration.
