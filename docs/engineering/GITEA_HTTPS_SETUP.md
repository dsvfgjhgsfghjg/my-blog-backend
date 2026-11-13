# Gitea HTTPS Behind the Existing Apache Frontend

## Chosen Topology

Expose Gitea at:

```text
https://leo-blog.top/gitea/
```

The existing `myblog-frontend` Apache container terminates TLS with the current
Let's Encrypt certificate and proxies `/gitea/` to `gitea-server-1.27:3000` over
the private `gitea-network`. No new DNS record or certificate is required.

Keep the direct Gitea HTTP port private after the HTTPS path is verified. Port 222
remains the Git SSH endpoint.

## Required Settings

Frontend repository:

- `frontend/apache.conf` contains the `/gitea/` proxy and WebSocket upgrade rule.
- The frontend container must join Docker network `gitea-network`.
- The image must be rebuilt and deployed after changing Apache configuration.

Backend deployment compose:

- `deploy/docker-compose.prod.yml` declares `gitea-network` as external and adds
  it to the frontend service.

Gitea:

```ini
[server]
DOMAIN = leo-blog.top
ROOT_URL = https://leo-blog.top/gitea/
HTTP_PORT = 3000
SSH_DOMAIN = leo-blog.top
SSH_PORT = 222

[session]
COOKIE_SECURE = true
```

If the container uses `GITEA__*` environment variables, those override
`app.ini`. Recreate the container with equivalent settings rather than editing
only `app.ini`.

## Deployment Order

1. Back up the current Gitea container definition and data directory.
2. Add the external `gitea-network` to the production frontend compose service.
3. Build and deploy the frontend image containing the new Apache config.
4. Recreate Gitea with the HTTPS `ROOT_URL`, secure cookies, and internal
   `HTTP_PORT=3000`.
5. Verify from inside the frontend container that
   `http://gitea-server-1.27:3000/api/v1/version` responds.
6. Verify the public HTTPS endpoints and redirects.
7. Change the published Gitea HTTP port to loopback-only, or remove the host
   port entirely if no host process needs it.
8. Update the Gitea runner instance URL to the HTTPS endpoint.

## Verification

Required checks:

- `https://leo-blog.top/gitea/` returns the Gitea UI over a valid certificate.
- `https://leo-blog.top/gitea/api/v1/version` returns JSON.
- Login sets the Gitea session cookie with `Secure` and `HttpOnly`.
- HTML, CSS, JavaScript, avatars, LFS, and repository archive downloads load
  without redirecting to `http://`.
- Git HTTPS clone works through `/gitea/<owner>/<repo>.git`.
- Gitea Actions logs and notification WebSockets work through the proxy.
- SSH clone through port 222 still works.
- `http://113.31.105.30:3000` is no longer reachable externally after the
  transition.

## Rollback

Keep the previous Gitea container definition and the previous frontend image tag.
If the proxy or Gitea URL configuration fails:

1. Restore the previous Gitea container definition.
2. Redeploy the previous frontend image.
3. Keep the direct Gitea port closed only if the rollback path does not depend on
   it.
4. Re-run the version endpoint and SSH clone checks before closing the maintenance
   window.
