# Deploy (NCP micro + Nginx + Docker backend)

## 1) Backend jar 준비(로컬/CI 권장)

```bash
./gradlew bootJar
cp build/libs/*.jar app.jar
```

## 2) 서버로 업로드

```bash
ssh ubuntu@picassolvebe.duckdns.org "sudo mkdir -p /opt/picassolve && sudo chown ubuntu:ubuntu /opt/picassolve"

# 서버에 Docker 빌드 컨텍스트(최소 파일) 업로드
scp Dockerfile docker-compose.yml app.jar ubuntu@picassolvebe.duckdns.org:/opt/picassolve/
scp picassolve.env.example ubuntu@picassolvebe.duckdns.org:/opt/picassolve/picassolve.env
```

서버에서 `/opt/picassolve/picassolve.env`의 DB/CORS 값을 실제 값으로 채웁니다.

## 3) 서버에서 실행

```bash
cd /opt/picassolve
docker build -t picassolve:latest .
docker rm -f picassolve 2>/dev/null || true
docker run -d --name picassolve \
  --restart unless-stopped \
  --env-file /opt/picassolve/picassolve.env \
  -p 127.0.0.1:8099:8099 \
  --memory 650m --memory-swap 1g \
  --log-opt max-size=10m --log-opt max-file=3 \
  picassolve:latest
```

또는 `docker-compose.yml`을 쓰면:

```bash
cd /opt/picassolve
docker compose up -d --build
```

## 4) Nginx

`deploy/nginx/picassolvebe.conf`를 `/etc/nginx/sites-available/picassolvebe`로 두고 활성화한 뒤 reload 합니다.

```bash
sudo ln -sf /etc/nginx/sites-available/picassolvebe /etc/nginx/sites-enabled/picassolvebe
sudo nginx -t
sudo systemctl reload nginx
```

## 5) 점검

```bash
curl -I http://127.0.0.1:8099/login
curl -I https://picassolvebe.duckdns.org/login
docker logs -n 100 picassolve
```
