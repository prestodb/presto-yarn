# Pre-requisities

* A cluster with HDP 2.2 or CDH5 installed
* JDK 1.8 
* Zookeeper 

# Create Slider App Package for Presto (Single Node)

## Preparing presto-server installation tarball
* Get the presto-server-0.110.tar.gz from Presto. 
* Untar the file to presto-server-0.110, create a configuration directory, etc under presto-server-0.110.
* Create node.properties, jvm.config and config.properties under this etc. For now, we manually create and populate it with appropriate values. Make sure you configure it for single-node mode (coordinator and worker on same node)
* The data directory's (added in node.properties eg: /var/presto/) should be owned by user yarn, otherwise slider will fail to start Presto with permission errors. 
* Now tar the presto-server-0.110 (including the etc directory) and put it under app-package/presto/package/files/presto-server-0.110.tar.gz

## Preparing other slider specific configuration

* Copy the app-package/appConfig-default.json and app-package/resources-default.json to appConfig.json and resources.json respectively. Update them with whatever configurations you want to have for Presto
* make jdk8 the default java or add it to "java_home" in your appConfig.json
* Prepare the slider app package by zipping the app-package/presto directory as presto.zip

The app package presto.zip should look something like:

```
unzip -l "$@" ../presto
Archive:  ../presto.zip
  Length      Date    Time    Name
---------  ---------- -----   ----
      562  07-15-2015 18:01   appConfig-default.json
     1964  07-15-2015 11:59   metainfo.xml
        0  07-15-2015 07:57   package/
        0  07-15-2015 23:00   package/scripts/
     1778  07-15-2015 23:00   package/scripts/prestoserver.py
     1414  07-15-2015 09:04   package/scripts/params.py
        0  07-16-2015 10:17   package/files/
403755481  07-16-2015 10:16   package/files/presto-server-0.110.tar.gz
     1823  07-15-2015 08:05   README.md
      278  07-15-2015 08:10   resources-default.json
---------                     -------
403763300                     10 files
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
$ hdfs dfs -chown -R /user/<user>
```
 
* Now run slider as <user>
```
su <user>
cd slider-0.80.0-incubating
bin/slider install-package --name PRESTO --package ../presto.zip
bin/slider create presto1 --template ../presto/appConfig.json --resources ../presto/resources.json (using modified .json files as per your requirement)
```

This should start your application, and you can see it under the Yarn RM webUI.

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
