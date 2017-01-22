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
import ast, os, shutil

def set_configuration(component=None):
    """
    Set configuration based on the component role. The jinja2 templates are populated from params.py
    :param component: COORDINATOR or WORKER
    :return:
    """
    import params

    if (os.path.exists(format("{conf_dir}"))):
        shutil.rmtree(format("{conf_dir}"))
                      
    _directory(params.conf_dir, params)
    _directory(params.catalog_dir, params)
    _directory(params.pid_dir, params)
    _directory(params.log_dir, params)

    _template_config("{params.conf_dir}/config.properties", params, component)
    _template_config("{params.conf_dir}/node.properties", params)
    _template_config("{params.conf_dir}/queues.json", params)

    with open(format("{params.conf_dir}/README.txt"), 'a') as fw:
        warning_string = 'DO NOT manually modify the configuration here. This is deployed via YARN-Slider and overwritten every time the application starts.'
        fw.write("%s" % warning_string)

    if params.jvm_args:
        _parse_array_and_write(params.jvm_args, format("{params.conf_dir}/jvm.config"))

    if params.jvm_args:
        _parse_array_and_write(params.log_properties, format("{params.conf_dir}/log.properties"))

    if params.additional_config_properties:        
        _parse_array_and_write(params.additional_config_properties, format("{params.conf_dir}/config.properties"))

    if params.additional_node_properties:
        _parse_array_and_write(params.additional_node_properties, format("{params.conf_dir}/node.properties"))

    if params.catalog_properties:
        catalog_dict = ast.literal_eval(params.catalog_properties)
        for key, value in catalog_dict.iteritems():
            _store_configuration(value, format("{params.catalog_dir}/{key}.properties"))

    if params.addon_plugins:
        plugins_dict = ast.literal_eval(params.addon_plugins)
        for key, value in plugins_dict.iteritems():
            plugin_dir = os.path.join(params.presto_plugin_dir, key)
            if not os.path.exists(plugin_dir):
                os.makedirs(plugin_dir)
            for jar in value:
                shutil.copy2(os.path.join(params.source_plugin_dir, jar), plugin_dir)


def _parse_array_and_write(parameters, path):
    arg_list = ast.literal_eval(parameters)
    _store_configuration(arg_list, path)
            
def _store_configuration(parameters, path):
    with open(path, 'a') as fw:
        for parameter in parameters:
            fw.write("%s\n" % parameter)
            
def _directory(path, params):
    Directory(path,
              owner=params.presto_user,
              group=params.user_group,
              recursive=True
              )


def _template_config(path, params, template_tag=None):
    TemplateConfig(format(path),
                   owner=params.presto_user,
                   group=params.user_group,
                   template_tag=template_tag
                   )
