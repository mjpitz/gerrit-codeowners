# CODEOWNERS for Gerrit

- [Features](#features)
- [What does it mean?](#what-does-it-mean-)
- [How to use it?](#how-to-use-it-)
- [How many reviewers are assigned?](#how-many-reviewers-are-assigned-)
- [Can I have less than 2 owners for one dir/repo?](#can-i-have-less-than-2-owners-for-one-dir-repo-)
- [Can I have more than 2 owners?](#can-i-have-more-than-2-owners-)
- [Can I use teams?](#can-i-use-teams-)
- [How to turn it off?](#how-to-turn-it-off-)
- [How to assign existing changes?](#how-to-assign-existing-changes-)
- [How can I make the world to be a better place?](#how-can-i-make-the-world-to-be-a-better-place-)
- [Would you like to improve it?](#would-you-like-to-improve-it-)
- [Oh, it doesn't work for me...](#oh--it-doesn-t-work-for-me)
- [I have different usernames for github and gerrit, am I using the right usernames?](#i-have-different-usernames-for-github-and-gerrit--am-i-using-the-right-usernames-)
- [Does it need to be in the main branch?](#does-it-need-to-be-in-the-main-branch-)

## Features

- [x] Auto-assign gerrit patches and pull requests based on CODEOWNERS.
- [x] Support for GitHub
- [ ] Support for GitLab?

## What does it mean?

If you have CODEOWNERS file in the root of a Gerrit repository with GitHub users, issues will be autoassigned to random
owners (in Gerrit!)

## How to use it?

Create a `CODEOWNERS` file in the root of your repository with the following syntax:

```
path   @githubuser
...
```

Path can be `*`, `*.go`, `dir/`

For more details of the syntax, check:
https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/about-code-owners?algolia-query=Code%20owners

## How many reviewers are assigned?

2 by default, but can be configured with a comment in CODEOWNERS:

```
#gerrit-codeowners.reviewer-count: 3
```

## Can I have less than 2 owners for one dir/repo?

Yes, you can.

You can also choose to fill missing reviewers based on git history (use `#gerrit-codeowners.use-git-history: true`
comment, to enable this feature)

## Can I have more than 2 owners?

Yes, you can. Assignment is predictable random (using hashes of the change id and users).

## Can I use teams?

Yes, it's based on GitHub teams (not Gerrit teams).
To make it work gerrit needs to be configured via `$GERRIT_SITE/etc/config`:

```
[plugin "codeowners"]
githubAppID = <github application id>
githubInstanceID = <github instance id>
githubKeyPath = <github key path>

# For testing a Personal Access Token can be used.
githubToken = <github jwt token>

# optional reviewer group allows to restrict who gets automatically assigned.
reviewerGroup = "name:Org"
```

## How to turn it off?

Remove CODEOWNERS file or change the `#gerrit-codeowners.reviewer-count:` to 0

## How to assign existing changes?

Assignment is automatic:

* When patch moved out from the WIP state
* When new revision is pushed
* When autoassign comment is added

## How can I make the world to be a better place?

Just adopt code path!

Just put your names to any `CODEOWNERS` for any path what you are comfortable with and give review when issues are
assigned to you. :pray:

## Oh, it doesn't work for me...

Support slack channel is `#team-delivery`

## How does it figure out the users?

The plugin tries to match-up the users by username and then by email.
Hence, the gerrit configuration should contain GitHub email.

## Does it need to be in the main branch?

The plugin uses the `CODEOWNERS` file in the target branch of the change.
