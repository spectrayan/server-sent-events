# Developer Certificate of Origin (DCO)

Version 1.1

Copyright (C) 2004, 2006 The Linux Foundation and its contributors.

Everyone is permitted to copy and distribute verbatim copies of this
license document, but changing it is not allowed.

---

By making a contribution to this project, I certify that:

(a) The contribution was created in whole or in part by me and I
    have the right to submit it under the open source license
    indicated in the file; or

(b) The contribution is based upon previous work that, to the best
    of my knowledge, is covered under an appropriate open source
    license and I have the right under that license to submit that
    work with modifications, whether created in whole or in part
    by me, under the same open source license (unless I am
    permitted to submit under a different license), as indicated
    in the file; or

(c) The contribution was provided directly to me by some other
    person who certified (a), (b) or (c) and I have not modified
    it.

(d) I understand and agree that this project and the contribution
    are public and that a record of the contribution (including all
    personal information I submit with it, including my sign-off) is
    maintained indefinitely and may be redistributed consistent with
    this project or the open source license(s) involved.

---

## How to Sign Off Commits

To certify that your contribution complies with the Developer Certificate of Origin, append a `Signed-off-by:` line to every git commit message.

### With Git Command Line
Git provides the `-s` flag to automate this:

```bash
git commit -s -m "feat(module): description of changes"
```

This appends your committer identity to the commit message:

```text
Signed-off-by: Your Name <your.email@example.com>
```

> **Note**: Ensure your git user name and email match your GitHub profile:
> ```bash
> git config --global user.name "Your Name"
> git config --global user.email "your.email@example.com"
> ```

### Fixing Unsigned Commits
If you forgot to sign off on commits in a pull request:

**For the most recent commit:**
```bash
git commit --amend -s --no-edit
git push --force-with-lease
```

**For multiple commits on your branch:**
```bash
git rebase --signoff origin/main
git push --force-with-lease
```
