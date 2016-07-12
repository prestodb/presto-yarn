# Presto-yarn [![Build Status](https://travis-ci.org/prestodb/presto-yarn.svg?branch=master)](https://travis-ci.org/prestodb/presto-yarn)

This project contains the code and needed to integrate Presto
`Presto <https://prestodb.io/>`_ with Apache Hadoop YARN using
`Apache Slider <https://slider.incubator.apache.org/>`_

Presto on YARN can be set up either manually using Apache Slider or via Ambari Slider Views if you are planning to use HDP distribution.

The full documentation can be found [here](https://prestodb.io/presto-yarn/).

## Building the project

Run ```mvn clean package``` and the presto app package will be packaged at ``presto-yarn-package/target/presto-yarn-package-<version>-<presto-version>.zip``.
To specify a specific version of Presto run ```mvn clean package  -Dpresto.version=<version>```

This .zip will have ``presto-server-<version>.tar.gz`` from Presto under ``package/files/``. The Presto installed will use the configuration templates under ``package/templates``.

The app package built should look something like:

::
   
     unzip -l "$@" ../presto-yarn-package-1.0.0-SNAPSHOT-0.130.zip

     Archive:  ../presto-yarn-package-1.0.0-SNAPSHOT-0.130.zip
       Length      Date    Time    Name
     ---------  ---------- -----   ----
	     0  2015-11-30 22:57   package/
	     0  2015-11-30 22:57   package/files/
     411459833  2015-11-30 20:26   package/files/presto-server-0.130.tar.gz
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
