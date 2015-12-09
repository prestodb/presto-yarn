#!/usr/bin/env bash

HDP_VERSION=$(hadoop version | head -n 1 | sed 's/Hadoop 2.6.0.//g')
sudo -u hdfs hdfs dfs -mkdir -p /hdp/apps/$HDP_VERSION/mapreduce
sudo -u hdfs hdfs dfs -put /usr/hdp/current/hadoop-client/mapreduce.tar.gz /hdp/apps/$HDP_VERSION/mapreduce/mapreduce.tar.gz
