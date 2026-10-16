"""Check raw author/committer emails only in an explicit, nonempty Git range."""

import argparse
import json
import os
import re
import subprocess
import sys


class RangeError(Exception):
    pass


def git(*args):
    # A local replace ref must not disguise the actual public commit metadata.
    env = dict(os.environ, GIT_NO_REPLACE_OBJECTS="1")
    result = subprocess.run(
        ["git", *args], env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE
    )
    if result.returncode:
        # Do not echo Git stderr or environment values that might contain credentials.
        raise RangeError("Git could not read the requested commits or repository.")
    return result.stdout.decode("utf-8", errors="replace").rstrip("\n")


def allowed_email(email):
    return email.lower() == "noreply@github.com" or re.fullmatch(
        r"[^@\s]+@users\.noreply\.github\.com", email.lower()
    ) is not None


def check_range(base, head):
    # Accept event-provided full object IDs, never revisions, options, or branch names.
    for label, sha in (("base", base), ("head", head)):
        if not re.fullmatch(r"(?:[0-9a-fA-F]{40}|[0-9a-fA-F]{64})", sha):
            raise RangeError(f"{label} must be a full commit SHA.")
        if set(sha) == {"0"}:
            raise RangeError(f"{label} is all zeroes; initial/deletion events need a reviewed range.")
        if git("rev-parse", "--verify", sha + "^{commit}").lower() != sha.lower():
            raise RangeError(f"{label} does not identify a commit directly.")
    if git("rev-parse", "--is-shallow-repository") != "false":
        raise RangeError("Shallow history is unsafe; fetch-depth must be 0.")

    commit_range = f"{base}..{head}"
    commits = git("rev-list", "--reverse", commit_range, "--").splitlines()
    print(f"Range: {commit_range}")
    print(f"New commits: {len(commits)}")
    if not commits:
        raise RangeError("Empty range: no new commits were checked; refusing to report PASS.")

    rejected = 0
    for sha in commits:
        # Lowercase %ae/%ce read the stored emails, without mailmap substitution.
        fields = git("show", "--no-patch", "--format=%H%x00%an%x00%ae%x00%cn%x00%ce", sha, "--").split("\0")
        if len(fields) != 5 or fields[0] != sha:
            raise RangeError("Cannot decode commit identity metadata.")
        full_sha, author, author_email, committer, committer_email = fields
        author_ok, committer_ok = allowed_email(author_email), allowed_email(committer_email)
        rejected += not (author_ok and committer_ok)
        # JSON escapes newlines/control characters in names and avoids log-command injection.
        print(json.dumps({
            "commit": full_sha[:12],
            "authorName": author, "authorEmail": author_email, "authorAllowed": author_ok,
            "committerName": committer, "committerEmail": committer_email, "committerAllowed": committer_ok,
        }, ensure_ascii=True))

    if rejected:
        print(f"FAIL: {rejected} of {len(commits)} commits use a non-GitHub-noreply identity.")
        return 1
    print(f"PASS: checked author and committer emails in all {len(commits)} new commits.")
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", required=True)
    parser.add_argument("--head", required=True)
    args = parser.parse_args()
    try:
        return check_range(args.base, args.head)
    except (RangeError, OSError) as error:
        # OS errors may contain local paths; only expected range messages are logged.
        message = str(error) if isinstance(error, RangeError) else "Cannot execute Git."
        print("ERROR: " + message, file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
