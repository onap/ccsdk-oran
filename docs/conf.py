from docs_conf.conf import *

branch = 'latest'
master_doc = 'index'

linkcheck_ignore = [
    'http://localhost',
]

extensions = ['sphinx_tabs.tabs', 'sphinxcontrib.redoc',]

redoc = [
            {
                'name': 'PMS API',
                'page': 'pms-api',
                'spec': '../a1-policy-management/api/pms-api.json',
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

html_last_updated_fmt = '%d-%b-%y %H:%M'

def setup(app):
    app.add_css_file("css/ribbon.css")
