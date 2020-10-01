from docs_conf.conf import *

branch = 'latest'
master_doc = 'index'

linkcheck_ignore = [
    'http://localhost',
]

extensions = ['sphinx_tabs.tabs', 'sphinxcontrib.redoc',
]

redoc = [
            {
                'name': 'PMS API',
                'page': './offeredapis/pms-api',
                'spec': './offeredapis/api_policyManagementService/swagger.json',
                'embed': True,
            }
        ]

redoc_uri = 'https://cdn.jsdelivr.net/npm/redoc@next/bundles/redoc.standalone.js'

intersphinx_mapping = {}

html_last_updated_fmt = '%d-%b-%y %H:%M'

def setup(app):
    app.add_css_file("css/ribbon.css")
