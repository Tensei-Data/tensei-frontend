# Contribution Guide
 
This project has adopted the [Collective Code Construction Contract
(C4.2)](https://rfc.zeromq.org/spec:42) for contributing. Please read it
before sending patches.
 
Everyone is expected to follow the
[Scala Code of Conduct](http://www.scala-lang.org/conduct.html) when
dicussing the project on the available communication channels.
If you are being harassed, please contact us immediately so that we can
support you.
 
### Additions to C4.2
 
1. This project is licensed under GNU Affero General Public License 3.
See [LICENSE](LICENSE) for details.
 
2. Contributors are listed in the file [AUTHORS.md](AUTHORS.md). Add
yourself if you have contributed.

3. Please maintain the existing code style and try to keep your commits
small and focused. The project follows the at large the 
[Scala Style Guide](http://docs.scala-lang.org/style/). Code is formatted
using [scalafmt](http://scalameta.org/scalafmt/) upon compilation. You
may also use a scalafmt plugin for your favourite editor/IDE.

4. Please rebase your branch if the project diverges from your branch.
 
5. Before a pull request is merged the commits done on the feature branch
SHOULD be squashed into a single commit.
 
6. Changes are documented in the file [CHANGELOG.md](CHANGELOG.md). Please
use the section `Unreleased` to note your changes.
 
## Release Guide
 
The changes in the section `Unreleased` in the [CHANGELOG.md](CHANGELOG.md)
file MUST be moved to a section named after the release and a new empty
`Unreleased` section MUST be created.
 
A release SHALL be accompanied by an annotated tag (`git tag -a NAME`) that
holds a description of the changes that are included in the release. This
description SHOULD be same as in the file [CHANGELOG.md](CHANGELOG.md).

