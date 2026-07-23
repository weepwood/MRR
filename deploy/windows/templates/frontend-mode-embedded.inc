# MRR frontend mode: embedded
proxy_pass http://mrr_backend;
include {{MRR_URI_ROOT}}/config/nginx/proxy.inc;
