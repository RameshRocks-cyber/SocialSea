# Backend wrapper (`SocialSea-main/`)

Some deployments are configured to build the backend from this subfolder.

To keep the deployed backend matching the local backend code at the repository root, this folder's `pom.xml` is configured to compile sources/resources from `../src/...` (the repo root backend).

Notes:
- Maven build/run from here works (it uses the root backend code).
- Docker builds must use the repository root as the build context (Docker can't `COPY` files from parent directories).
