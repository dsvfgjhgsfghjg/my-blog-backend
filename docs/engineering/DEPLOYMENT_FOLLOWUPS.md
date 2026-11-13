# Deployment Follow-ups

## After the first verified production deployment

- Confirm backend and frontend releases were started by explicit
  `workflow_dispatch`, not by merging a pull request.
- Remind the user to resume the GitHub public-mirror cleanup.
- Confirm the GitHub push credential is available outside the prompt and is not
  stored in either repository.
- Force-push the sanitized backend and frontend histories with
  `--force-with-lease`.
- Verify that the old database dump no longer resolves on the public mirror.
- Run Gitleaks against every public GitHub branch.
- Record the GitHub branch SHAs in the security audit report.

## Gitea credential handling

Do not store or transmit the Gitea account password in prompts, repository files,
shell history, or process arguments.

Preferred workflow:

1. Create a short-lived Gitea access token in the Gitea web UI.
2. Run `scripts/security/store-gitea-token.sh` in a local interactive terminal.
3. Enter the token without echo; it is stored in the system keyring.
4. Use `GITEA_API_URL=<url> scripts/security/gitea-api.sh ...` for API calls.
5. Revoke the Gitea token and run `scripts/security/remove-gitea-token.sh` when
   maintenance is complete.

Plain HTTP is rejected except for loopback endpoints. If Gitea is not available
over HTTPS or a local SSH tunnel, do not send credentials to it.
