proxy_http_version 1.1;
proxy_set_header Host $host;
proxy_set_header X-Real-IP $remote_addr;
proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
proxy_set_header X-Forwarded-Proto $scheme;
# Nginx owns the public request identifier; client-provided values are overwritten.
proxy_set_header X-Request-Id $request_id;
proxy_set_header Connection "";
proxy_connect_timeout 5s;
proxy_send_timeout 120s;
proxy_read_timeout 120s;
