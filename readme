[![obsolete JetBrains project](https://jb.gg/badges/obsolete-flat-square.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)

Experimental trigger for pull-request */merge branches.

Github creates pull-request merge branches (refs/heads/*/merge) for
every pull request. Such branches point to the merge commit between a
base branch and a pull-request. These merge commits are updated when
someone pushes a new commit to the base branch. This causes redundant
builds triggered by VCS trigger in TeamCity: builds are triggered
because revision of the refs/pull/XX/merge branch changed
(https://youtrack.jetbrains.com/issue/TW-33455).

This trigger solves redundant builds problem. It tracks revisions of
the pull-requests (refs/pull/*/head branches) and runs a build in the
corresponding refs/pull/XX/merge branch when a pull-request is created
or updated, but doesn't run builds on push into the base branch.
