# SocialSea EC2 Deployment (`socialsea.co.in`)

This setup hosts both frontend and backend on one EC2 instance:
- `https://socialsea.co.in` -> Vite frontend static files
- `https://socialsea.co.in/api/*` -> Spring Boot backend on `127.0.0.1:8080`
- `https://socialsea.co.in/ws` -> backend websocket endpoint

## 1) DNS + EC2 prerequisites
- Ubuntu 22.04 EC2
- Attach Elastic IP to EC2
- Route53/registrar DNS records:
  - `A @` -> Elastic IP
  - `A www` -> Elastic IP
- EC2 security group inbound:
  - `22` (SSH)
  - `80` (HTTP)
  - `443` (HTTPS)

## 2) Install dependencies (Java 17 required)
```bash
sudo apt update
sudo apt install -y git nginx certbot python3-certbot-nginx openjdk-17-jdk maven

# Node 20
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt install -y nodejs

java -version
node -v
npm -v
```

## 3) Clone repos on EC2
```bash
sudo mkdir -p /opt/socialsea
sudo chown -R $USER:$USER /opt/socialsea
cd /opt/socialsea

# backend
git clone <BACKEND_REPO_URL> backend

# frontend
git clone <FRONTEND_REPO_URL> frontend
```

## 4) Configure backend env
```bash
cd /opt/socialsea/backend
cp .env.production.example .env.production
nano .env.production
```
Set at minimum:
- `SPRING_PROFILES_ACTIVE=prod`
- `APP_RUNTIME_EC2=true`
- `APP_RUNTIME_ENFORCE_PROD_ON_EC2=true`
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `JWT_SECRET`
- `CLOUDINARY_CLOUD_NAME`
- `CLOUDINARY_API_KEY`
- `CLOUDINARY_API_SECRET`
- `APP_FRONTEND_BASE_URL=https://socialsea.co.in`
- `APP_SECURITY_ALLOWED_ORIGINS=https://socialsea.co.in,https://www.socialsea.co.in`
- `LIVEKIT_URL`
- `LIVEKIT_API_KEY`
- `LIVEKIT_API_SECRET`

Important:
- Do not keep placeholder values like `<cloud-name>` or `<db-user>` in `.env.production`.
- Keep `APP_UPLOAD_ALLOW_LOCAL_FALLBACK=false` in production.

## 5) Build and run backend as service
```bash
cd /opt/socialsea/backend
mvn -DskipTests clean package

# Create/update a stable jar path for systemd (avoid wildcard ExecStart issues)
JAR_PATH=$(ls -1 /opt/socialsea/backend/target/socialsea-*.jar | grep -v '\.original$' | head -n 1)
ln -sfn "$JAR_PATH" /opt/socialsea/backend/socialsea.jar
```

Create `/etc/systemd/system/socialsea-backend.service`:
```ini
[Unit]
Description=SocialSea Spring Boot API
After=network.target

[Service]
User=ubuntu
WorkingDirectory=/opt/socialsea/backend
EnvironmentFile=/opt/socialsea/backend/.env.production
ExecStart=/usr/bin/java -Dspring.profiles.active=prod -jar /opt/socialsea/backend/socialsea.jar
SuccessExitStatus=143
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

Enable and start:
```bash
sudo systemctl daemon-reload
sudo systemctl enable socialsea-backend
sudo systemctl restart socialsea-backend
sudo systemctl status socialsea-backend --no-pager
```

Check runtime config was loaded correctly:
```bash
sudo journalctl -u socialsea-backend -n 200 --no-pager | grep -E "profile is active|Production runtime config validated|Missing required production configuration|HikariPool-1 - Added connection"
```

Expected:
- profile must be `prod`
- you should see `Production runtime config validated`
- datasource should be your real DB host (not h2/local temp db)

## 6) Build frontend
```bash
cd /opt/socialsea/frontend
cp .env.production.example .env.production
```

Edit `.env.production` for same-domain API:
```env
VITE_API_BASE_URL=/api
VITE_API_URL=/api
```

Important:
- Do not use `https://api.socialsea.co.in` unless you have actually created and configured that subdomain.

Build and publish:
```bash
npm ci
npm run build
sudo mkdir -p /var/www/socialsea
sudo rsync -av --delete dist/ /var/www/socialsea/
```

## 7) Nginx: frontend + API proxy on same domain
Create `/etc/nginx/sites-available/socialsea`:
```nginx
server {
    server_name socialsea.co.in www.socialsea.co.in;

    root /var/www/socialsea;
    index index.html;
    client_max_body_size 220M;

    location / {
        try_files $uri $uri/ /index.html;
    }

    # Keep external /api/actuator/* while backend serves /actuator/*
    location /api/actuator/ {
        proxy_pass http://127.0.0.1:8080/actuator/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /api/ {
        # IMPORTANT: no trailing slash so /api/* reaches backend as /api/*
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /ws {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 3600;
        proxy_send_timeout 3600;
    }
}
```

Enable config:
```bash
sudo rm -f /etc/nginx/sites-enabled/default
sudo ln -sf /etc/nginx/sites-available/socialsea /etc/nginx/sites-enabled/socialsea
sudo nginx -t
sudo systemctl reload nginx
```

## 8) Enable HTTPS
```bash
sudo certbot --nginx -d socialsea.co.in -d www.socialsea.co.in
```

## 9) Verification
```bash
curl -I https://socialsea.co.in
curl -I https://socialsea.co.in/api/actuator/health
curl -sS https://socialsea.co.in/api/actuator/health
```

## 10) Update deployment (next releases)
```bash
# backend
cd /opt/socialsea/backend
git pull
mvn -DskipTests clean package
JAR_PATH=$(ls -1 /opt/socialsea/backend/target/socialsea-*.jar | grep -v '\.original$' | head -n 1)
ln -sfn "$JAR_PATH" /opt/socialsea/backend/socialsea.jar
sudo systemctl restart socialsea-backend

# frontend
cd /opt/socialsea/frontend
git pull
npm ci
npm run build
sudo rsync -av --delete dist/ /var/www/socialsea/
sudo systemctl reload nginx
```
