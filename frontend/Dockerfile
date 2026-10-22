# Front-end image (DEP-01): the shell and the 3 remotes as static files, served by Caddy.
# Build context: frontend/.
#
#   docker build -t soukly-frontend:local frontend
#   docker run --rm -p 8080:8080 -e PUBLIC_BASE_URL=http://localhost:8080 soukly-frontend:local
#
# The federation manifest is not built in: docker/entrypoint.sh writes it at startup from
# PUBLIC_BASE_URL, so the same image runs in dev and in prod. Same for the contact address of the
# legal pages (LEGAL_CONTACT_EMAIL).

# ---- Build: production build of the shell and the remotes ----
FROM node:24.22.0-trixie-slim@sha256:7f486cabe892bff8fef04c0f7da8dba84a560be3e4e2740b7a86afc57ebec315 AS build

ENV CI=true \
    NX_DAEMON=false \
    NX_NO_CLOUD=true \
    COREPACK_ENABLE_DOWNLOAD_PROMPT=0

WORKDIR /workspace
# pnpm comes from Corepack, at the version of "packageManager" in package.json.
RUN corepack enable
COPY . .
# Tailwind's automatic source detection honours .gitignore, which lives at the repository root,
# outside this build context: without it, Tailwind also scans the outputs of the remotes built in
# parallel and the CSS differs from a local build.
RUN printf 'dist/\n.nx/\n.angular/\n' > .gitignore
RUN --mount=type=cache,target=/pnpm-store,id=soukly-pnpm \
    pnpm install --frozen-lockfile --store-dir /pnpm-store \
    && pnpm nx run-many -t build -p shell mfe-account mfe-catalog mfe-publish --configuration=production \
    && node tools/build-legal/build-legal.mjs --out /out/legal

# The local manifest of apps/shell/public points to localhost: it never ships.
RUN mkdir -p /out/remotes \
    && cp -r dist/apps/shell/browser /out/shell \
    && rm /out/shell/federation.manifest.json \
    && for mfe in mfe-account mfe-catalog mfe-publish; do cp -r "dist/apps/${mfe}/browser" "/out/remotes/${mfe}"; done

# ---- Runtime: Caddy, non-root, port 8080 ----
FROM caddy:2.11.7-alpine@sha256:d8542f48d34a9cf4e4c11a478865229840e87e4c96ea3f439101f31a5d35f75f

# The official binary carries the cap_net_bind_service file capability, useless on port 8080 and
# refused by the kernel under "no-new-privileges": a plain copy drops it.
RUN cp /usr/bin/caddy /tmp/caddy && mv /tmp/caddy /usr/bin/caddy \
    && addgroup -S -g 10001 soukly \
    && adduser -S -D -H -u 10001 -G soukly -s /sbin/nologin soukly \
    && mkdir -p /srv/runtime \
    && chown -R 10001:10001 /srv/runtime /data /config

COPY --from=build /out/shell /srv/shell
COPY --from=build /out/remotes /srv/remotes
# Legal pages (LEG-01), with the contact address placeholder: entrypoint.sh copies them to /srv/runtime
COPY --from=build /out/legal /srv/legal
COPY docker/Caddyfile /etc/caddy/Caddyfile
RUN caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
COPY --chmod=0755 docker/entrypoint.sh /usr/local/bin/entrypoint.sh

USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
