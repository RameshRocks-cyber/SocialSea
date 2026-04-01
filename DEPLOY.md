# SocialSea Deployment

## Frontend (Netlify)
- Project path: `socialsea-client-main/socialsea-client-main`
- Build command: `npm run build`
- Publish directory: `dist`
- SPA routing: configured in `netlify.toml`
- Production API URL: `.env.production` -> `VITE_API_URL=https://api.socialsea.co.in`
  - If you proxy `/api` from `https://socialsea.co.in` to the backend, set this to `https://socialsea.co.in` instead.

### Netlify env vars
- `VITE_API_URL=https://api.socialsea.co.in`
- `VITE_LIVEKIT_URL=wss://socialsea-mb50m9kr.livekit.cloud`

## Backend (Spring Boot)
- Project path: `SocialSea-main`
- Java: 17
- Build: `mvn -DskipTests package`
- Run (prod): `java -Dspring.profiles.active=prod -jar target/*.jar`

### Required backend env vars
- `SPRING_PROFILES_ACTIVE=prod`
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `JWT_SECRET`
- `CLOUDINARY_CLOUD_NAME`
- `CLOUDINARY_API_KEY`
- `CLOUDINARY_API_SECRET`
- `APP_FRONTEND_BASE_URL=https://socialsea.co.in`
- `LIVEKIT_URL=wss://socialsea-mb50m9kr.livekit.cloud`
- `LIVEKIT_API_KEY`
- `LIVEKIT_API_SECRET`

### Optional backend env vars
- `JWT_EXPIRATION` (default `86400000`)
- `APP_OTP_ALLOW_EMAIL_FAILURE` (default false)
- `APP_OTP_EXPOSE_DEBUG_OTP` (default false)
- `APP_OTP_RETURN_FALLBACK_OTP_ON_DELIVERY_FAILURE` (default false)

## Verification checklist
1. Frontend loads at `https://socialsea.co.in`.
2. API reachable at `https://socialsea.co.in/api/...`.
3. Login/register works.
4. Feed, reels, chat, notifications work.
5. Upload and long-video pages work.
