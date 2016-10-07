Developers
==========

Create Presto App Package
-------------------------

First step is to build the ``presto-yarn-package-<version>-<presto-version>.zip`` package to deploy Presto on YARN.

Run ```mvn clean package``` and the presto app package will be packaged at ``presto-yarn-package/target/presto-yarn-package-<version>-<presto-version>.zip``.

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

