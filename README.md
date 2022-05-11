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
- [License](#license)

## Features

- [x] Auto-assign gerrit patches and pull requests based on CODEOWNERS.
- [x] Support for GitHub
- [ ] Support for GitLab?

## What does it mean?

If you have CODEOWNERS file in the root of a Gerrit repository with GitHub users, issues will be autoassigned to random owners (in Gerrit!)

## How to use it?

Create a `CODEOWNERS` file in the root of your repository with the following syntax:
```
path   @githubuser
...
```

Path can be *, *.go, dir/

For more details of the syntax, check:
https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/about-code-owners?algolia-query=Code%20owners

## How many reviewers are assigned?

2 by default, but can be configured with a comment in CODEOWNERS:

```
#gerrit-codeowners.reviewer-count: 3
```

## Can I have less than 2 owners for one dir/repo?

Yes, you can. (When `CODEOWNERS` exists, plugin may try to find other owners based on Git history..., this might be changed based on feedback...)

## Can I have more than 2 owners?

Yes, you can. Assignment is predictable random (using hashes of the change id and users).

## Can I use teams?

Yes, it's based on GitHub teams (not Gerrit teams), but infra configuration is still work in progress (== don't use it, yet, will be available soon)

## How to turn it off?

Remove CODEOWNERS file or change the `#gerrit-codeowners.reviewer-count:` to 0

## How to assign existing changes?

Assignment is automatic:

* When patch moved out from the WIP state
* When new revision is pushed
* When autoassign comment is added

## How can I make the world to be a better place?

Just adopt code path!

Just put your names to any `CODEOWNERS` for any path what you are comfortable with and give review when issues are assigned to you. :pray:

## Would you like to improve it?

Open an issue/PR: https://github.com/storj/gerrit-codeowners/

## Oh, it doesn't work for me...

Support slack channel is `#team-dev-enablement`

## I have different usernames for github and gerrit, am I using the right usernames?

1. Use github usernames
2. you can test what plugins does:

eg. in case of using `@USERNAME` in `CODEOWNERS`:
```
curl -u elek:$(pass show -o storj/gerrit.http) 'https://review.dev.storj.io/a/accounts/?q=username:USERNAME' 
```
Where `pass show -o storj/gerrit.http` should be replaced with a command which prints out the gerrit HTTP password. (Assuming you wouldn't like to include it in your shell history).

## Does it need to be in the main branch?

The plugin uses the `CODEOWNERS` file in the target branch of the change.

## License

See [LICENSE](LICENSE) for more details.

```text
The MIT License (MIT)

Copyright (c) 2022 Mya Pitzeruse
```
