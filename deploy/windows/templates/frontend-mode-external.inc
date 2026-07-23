# MRR frontend mode: external
root {{MRR_URI_ROOT}}/current/frontend;
try_files $uri $uri/ /index.html;
