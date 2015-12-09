#!/usr/bin/env python
"""
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

"""

from resource_management import *
import uuid

# server configurations
config = Script.get_config()

java8_home = config['hostLevelParams']['java_home']

app_root = config['configurations']['global']['app_root']
app_name = config['configurations']['global']['app_name']

presto_root = format("{app_root}/{app_name}")
conf_dir = format("{presto_root}/etc")
catalog_dir = format("{conf_dir}/catalog")
presto_plugin_dir = format("{presto_root}/plugin")
source_plugin_dir = config['configurations']['global']['app_pkg_plugin']
addon_plugins = default('/configurations/global/plugin', '')

presto_user = config['configurations']['global']['app_user']
user_group = config['configurations']['global']['user_group']

data_dir = config['configurations']['global']['data_dir']
pid_dir = format("{data_dir}/var/run")
pid_file = format("{pid_dir}/slider_launcher.pid")
log_dir = format("{data_dir}/var/log")
log_file = format("{log_dir}/server.log")

singlenode = config['configurations']['global']['singlenode']
coordinator_host = config['configurations']['global']['coordinator_host']
presto_query_max_memory = config['configurations']['global']['presto_query_max_memory']
presto_query_max_memory_per_node = config['configurations']['global']['presto_query_max_memory_per_node']
presto_server_port = config['configurations']['global']['presto_server_port']
jvm_args = default('/configurations/global/jvm_args', '')

node_id = uuid.uuid1()

catalog_properties = default('/configurations/global/catalog', '')
additional_config_properties=default('/configurations/global/additional_config_properties', '')
additional_node_properties=default('/configurations/global/additional_node_properties', '')
