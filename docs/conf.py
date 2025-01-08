#  ============LICENSE_START===============================================
#  Copyright (C) 2020-2023 Nordix Foundation. All rights reserved.
#  Copyright (C) 2024 OpenInfra Foundation Europe. All rights reserved.
#  ========================================================================
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#  ============LICENSE_END=================================================
#

project = "onap"
release = "oslo"
version = "oslo"
# Map to 'latest' if this file is used in 'latest' (master) 'doc' branch.
# Change to {releasename} after you have created the new 'doc' branch.
branch = 'oslo'


author = "Open Network Automation Platform"
# yamllint disable-line rule:line-length
copyright = "ONAP. Licensed under Creative Commons Attribution 4.0 International License"

pygments_style = "sphinx"
html_theme = "sphinx_rtd_theme"
html_theme_options = {
  "style_nav_header_background": "white",
  "sticky_navigation": "False" }
html_logo = "_static/logo_onap_2024.png"
html_favicon = "_static/favicon.ico"
html_static_path = ["_static"]
html_show_sphinx = False
html_extra_path = ["offeredapis/openapitoolgen"]

extensions = [
    'sphinx.ext.intersphinx',
    'sphinx.ext.graphviz',
    'sphinxcontrib.blockdiag',
    'sphinxcontrib.seqdiag',
    'sphinxcontrib.swaggerdoc',
    'sphinxcontrib.plantuml',
    'sphinxcontrib.redoc',
    'sphinx_tabs.tabs'
]

redoc = [
            {
                'name': 'PMS API',
                'page': 'offeredapis/pms-api',
                'spec': './offeredapis/swagger/pms-api.json',
                'embed': True,
            },
            {
                'name': 'PMS API V3',
                'page': 'offeredapis/pms-api-v3',
                'spec': './offeredapis/swagger/pms-api-v3.json',
                'embed': True,
            },
            {
                'name': 'A1 ADAPTER API',
                'page': 'offeredapis/a1-adapter-api',
                'spec': './offeredapis/swagger/a1-adapter-api.json',
                'embed': True,
            }
        ]

redoc_uri = 'https://cdn.jsdelivr.net/npm/redoc@next/bundles/redoc.standalone.js'

intersphinx_mapping = {}
doc_url = 'https://docs.onap.org/projects'
master_doc = 'index'

exclude_patterns = ['.tox']

spelling_word_list_filename='spelling_wordlist.txt'
spelling_lang = "en_GB"

html_extra_path = [
    'offeredapis/openapitoolgen'
]

#
# Example:
# intersphinx_mapping['onap-aai-aai-common'] = ('{}/onap-aai-aai-common/en/%s'.format(doc_url) % branch, None)
#

html_last_updated_fmt = '%d-%b-%y %H:%M'

def setup(app):
    app.add_css_file("css/ribbon.css")

linkcheck_ignore = [
    r'http://localhost:\d+/',
    './a1-adapter-api.html', #Generated file that doesn't exist at link check.
    './pms-api.html',  #Generated file that doesn't exist at link check.
    './pms-api-v3.html',  #Generated file that doesn't exist at link check.
    './a1pms-api-v3.html' #Generated file that doesn't exist at link check.
]