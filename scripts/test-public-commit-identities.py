"""Exercise the public identity guard in disposable repos, never real project history."""

import importlib.util
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

# Importing the checker for rule tests must not leave untracked bytecode in scripts/.
sys.dont_write_bytecode = True

SCRIPT = Path(__file__).with_name("check-public-commit-identities.py").resolve()
NOREPLY = "fixture@users.noreply.github.com"
# Synthetic test data only; this is not a maintainer identity.
PERSONAL = "identity-guard-fixture@gmail.com"


class IdentityGuardTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="identity-guard-test-")
        self.addCleanup(self.temp.cleanup)
        self.repo = Path(self.temp.name)
        self.serial = 0
        self.env = dict(os.environ, GIT_CONFIG_NOSYSTEM="1", GIT_CONFIG_GLOBAL=os.devnull,
                        GIT_AUTHOR_NAME="Fixture author", GIT_COMMITTER_NAME="Fixture committer",
                        GIT_AUTHOR_EMAIL=NOREPLY, GIT_COMMITTER_EMAIL=NOREPLY)
        self.git("init", "--initial-branch=main")
        # A pre-existing personal identity is deliberately outside every test range.
        self.base = self.commit(author=PERSONAL)

    def git(self, *args, env=None, input=None):
        return subprocess.check_output(["git", *args], cwd=self.repo, env=env or self.env,
                                       input=input, stderr=subprocess.PIPE).decode().strip()

    def commit(self, author=NOREPLY, committer=NOREPLY):
        self.serial += 1
        env = dict(self.env, GIT_AUTHOR_EMAIL=author, GIT_COMMITTER_EMAIL=committer)
        self.git("-c", "core.hooksPath=" + str(self.repo / "no-hooks"), "commit",
                 "--allow-empty", "--no-gpg-sign", "-m", f"Synthetic identity fixture {self.serial}", env=env)
        return self.git("rev-parse", "HEAD")

    def guard(self, base, head):
        return subprocess.run([sys.executable, str(SCRIPT), "--base", base, "--head", head],
                              cwd=self.repo, env=self.env, capture_output=True, text=True)

    def test_all_noreply_and_old_personal_ancestor_excluded(self):
        self.commit()
        head = self.commit(author="123+another-user@users.noreply.github.com")
        result = self.guard(self.base, head)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("New commits: 2", result.stdout)
        self.assertNotIn(PERSONAL, result.stdout)

    def test_personal_author_fails(self):
        result = self.guard(self.base, self.commit(author=PERSONAL))
        self.assertEqual(result.returncode, 1)
        self.assertIn('"authorAllowed": false', result.stdout)

    def test_personal_committer_fails(self):
        result = self.guard(self.base, self.commit(committer=PERSONAL))
        self.assertEqual(result.returncode, 1)
        self.assertIn('"committerAllowed": false', result.stdout)

    def test_github_merge_committer_and_all_merge_parents(self):
        first = self.commit()
        self.git("checkout", "-b", "side", self.base)
        side = self.commit()
        tree = self.git("rev-parse", side + "^{tree}")
        env = dict(self.env, GIT_COMMITTER_EMAIL="noreply@github.com")
        merge = self.git("-c", "commit.gpgSign=false", "commit-tree", tree,
                         "-p", first, "-p", side, env=env, input=b"Synthetic merge\n")
        result = self.guard(self.base, merge)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("New commits: 3", result.stdout)
        self.assertIn('"committerEmail": "noreply@github.com"', result.stdout)

    def test_bad_intermediate_commit_not_hidden_by_good_head_or_mailmap(self):
        self.commit(author=PERSONAL)
        (self.repo / ".mailmap").write_text(f"Mapped <{NOREPLY}> <{PERSONAL}>\n", encoding="utf-8")
        head = self.commit()
        result = self.guard(self.base, head)
        self.assertEqual(result.returncode, 1)
        self.assertIn("New commits: 2", result.stdout)
        self.assertIn(PERSONAL, result.stdout)

    def test_email_domains_are_exact(self):
        spec = importlib.util.spec_from_file_location("identity_guard", SCRIPT)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        for email in ("x@noreply.example.com", "x@users.noreply.github.com.evil.example",
                      "x@evilusers.noreply.github.com", "@users.noreply.github.com",
                      "x@@users.noreply.github.com", "x y@users.noreply.github.com",
                      "person@example.com", "x@noreply.github.com", "x@users.noreply.gıthub.com"):
            with self.subTest(email=email):
                self.assertFalse(module.allowed_email(email))

    def test_empty_range_fails_explicitly(self):
        result = self.guard(self.base, self.base)
        self.assertEqual(result.returncode, 2)
        self.assertIn("New commits: 0", result.stdout)
        self.assertIn("Empty range", result.stderr)

    def test_unresolvable_commit_fails(self):
        result = self.guard(self.base, "1" * 40)
        self.assertEqual(result.returncode, 2)
        self.assertIn("ERROR:", result.stderr)

    def test_zero_or_revision_expression_fails(self):
        for value in ("0" * 40, "HEAD", "", "--all"):
            with self.subTest(value=value):
                self.assertEqual(self.guard(self.base, value).returncode, 2)

    def test_shallow_history_fails(self):
        self.commit()
        self.git("clone", "--depth=2", self.repo.as_uri(), str(self.repo / "shallow"))
        result = subprocess.run([sys.executable, str(SCRIPT), "--base", self.base, "--head",
                                 self.git("rev-parse", "HEAD")], cwd=self.repo / "shallow",
                                env=self.env, capture_output=True, text=True)
        self.assertEqual(result.returncode, 2)
        self.assertIn("Shallow history is unsafe", result.stderr)


if __name__ == "__main__":
    unittest.main(verbosity=2)
