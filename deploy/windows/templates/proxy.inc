proxy_http_version 1.1;
proxy_set_header Host $host;
proxy_set_header X-Real-IP $remote_addr;
# 当前为单层内网 Nginx，必须覆盖客户端自行提交的 X-Forwarded-For，避免伪造来源 IP。
proxy_set_header X-Forwarded-For $remote_addr;
proxy_set_header X-Forwarded-Proto $scheme;
proxy_set_header Connection "";
proxy_connect_timeout 5s;
proxy_send_timeout 120s;
proxy_read_timeout 120s;
