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

presto_user = config['configurations']['global']['app_user']
user_group = config['configurations']['global']['user_group']

data_dir = config['configurations']['global']['data_dir']
pid_dir = format("{data_dir}/var/run")
pid_file = format("{pid_dir}/slider_launcher.pid")

singlenode = config['configurations']['global']['singlenode']
coordinator_host = config['configurations']['global']['coordinator_host']
heapsize = config['configurations']['global']['presto_jvm_heapsize']

node_id = uuid.uuid1()

catalog_properties = config['configurations']['global']['catalog']
