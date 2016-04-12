#!/usr/bin/env bash

HDP_VERSION=$(hadoop version | head -n 1 | sed 's/Hadoop 2.7.1.//g')
su - hdfs -c "hdfs dfs -mkdir -p /hdp/apps/$HDP_VERSION/mapreduce"
su - hdfs -c "hdfs dfs -put /usr/hdp/current/hadoop-client/mapreduce.tar.gz /hdp/apps/$HDP_VERSION/mapreduce/mapreduce.tar.gz"
