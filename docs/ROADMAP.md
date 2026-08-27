# Release roadmap

## Test milestone

- Let the GitHub Actions workflow compile the installable APK.
- Replace the placeholder application ID and GitHub repository.
- Add an original launcher icon and app name after trademark review.
- Test accented names, regional forms, offline behavior, rotation, and screen readers.
- Create a signing key and keep it outside source control.
- Distribute GitHub workflow APK artifacts to private testers.

## Public milestone

- Add a hosted, authenticated leaderboard API with rate limiting and score validation.
- Add privacy policy, data safety disclosure, and a score-removal process.
- Add cached question packs so an interrupted connection cannot stop a run.
- Mirror approved commits from the private repository to the public repository.
- Publish a signed release APK and set `GITHUB_REPOSITORY` to that public repository.
