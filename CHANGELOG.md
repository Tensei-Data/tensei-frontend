# ChangeLog for the Tensei-Data Frontend

All notable changes to this project will be documented in this file.
This project adheres to [Semantic Versioning](http://semver.org/).

## Conventions when editing this file.

Please follow the listed conventions when editing this file:

* one subsection per version
* reverse chronological order (latest entry on top)
* write all dates in iso notation (`YYYY-MM-DD`)
* each version should group changes according to their impact:
    * `Added` for new features.
    * `Changed` for changes in existing functionality.
    * `Deprecated` for once-stable features removed in upcoming releases.
    * `Removed` for deprecated features removed in this release.
    * `Fixed` for any bug fixes.
    * `Security` to invite users to upgrade in case of vulnerabilities.

## Unreleased

## 1.13.3 (2017-06-02)

### Changed

- lots of code cleanup and some adjustments for code style (wartremover)

## 1.13.2 (2017-05-24)

### Added

- warning if usage of reserved sql names for tables or columns is detected

### Changed

- better error messages for DFASDL validation

### Fixed

- DFASDL validation shows no error upon server error

## 1.13.1 (2017-05-18)

### Changed

- cookbook export now in binary format

### Fixed

- forcing import of cookbook fails
- cookbook import fails if file encoding was changed
- cookbook import problems accross operating system boundaries
- usage of `java.io.FileInputStream` considered harmful

## 1.13.0 (2017-05-03)

### Changed

- restructure sbt configuration
- switch to scalafmt for code formatting
- update Scala to 2.11.11

### Removed

- lightbend activator due to EOL

### Fixed

- White Screen of Death upon import of broken cookbook files

## 1.12.1 (2017-03-22)

- no changes

## 1.12.0 (2017-03-13)

### Added

- options field for setting the locale on the `LowerOrUpper` transformer

### Changed

- update dfasdl-core and dfasdl-utils to 1.25.0
- update sbt-native-packager to 1.2.0-M8
- update sbt-wartremover to 1.3.0

### Removed

- remove unused code

### Fixed

- use of lower and uppercase string functions lead to locale specific issues

## 1.11.0 (2017-01-20)

### Added

- introduction of Tensei concepts for new users
- link to external documentation in new browser window

## 1.10.0 (2017-01-04)

### Added

- transformation configurations are marked "dirty" if connected cookbook changes dfasdl resources
- allow the same configuration information to be used multiple times within a transformation configuration
- support for using H2 database (which is the new default)
- allow changing to PostgreSQL by configuration
- link to free trial request from license page

### Changed

- update Play Framework to 2.5.10
- use H2 database by default
- adjusted packaging of .deb file and `init-production-conf.sh`
- show more details for triggers

### Removed

- support for jetty triggers because of instabilities and crashes

### Fixed

- link to cookbook editor broken on detail page
- changing dfasdl resources in cookbook breaks transformation configurations
- execution of "dirty" transformation configurations possible
- several bugs in database schema (mixed case of table and column names)
- license details not shown on license page
- crash when renaming a cookbook

## 1.9.2 (2016-11-30)

### Changed

- specify scala version using `in ThisBuild` in sbt
- update sbt-pgp to 1.0.1
- update sbt-wartremover to 1.2.1
- update sbt-native-packager to 1.2.0-M7
- update scalacheck to 1.13.4
- Code Cleanup in sbt files

### Fixed

- license expiration date not shown correctly
- Internal Server Error if dfasdl schema (xsd) could not be loaded

## 1.9.1 (2016-11-22)

- no changes

## 1.9.0 (2016-11-10)

### Added

- execute tests before building debian package
- defaults for logback configuration options
- logback configuration option in `application.ini`
- activator binary 1.3.12
- simple transformer for converting strings into long numbers

### Changed

- update Akka to 2.4.12
- update Play Framework to 2.5.9
- adjusted code according to Play update (massive changes!)
- code cleanup
- update SBT to 0.13.13
- update sbt-wartremover to 1.1.1
- update sbt-native-packager to 1.2.0-M5
- update postgresql driver to 9.4.1212
- update bootstrap to 3.3.7
- websocket for agent run logs now returns json instead of strings
- more fine grained logging configuration
- logfile is now called `tensei-frontend.log`
- use async logfile appender and rotate logfiles by default

### Removed

- compiler flag `-Xfatal-warnings` (play2-auth needs some deprecated methods)
- custom templates for sbt-native-packager

### Fixed

- agent run logs loaded in endless loop
- loglines saved multiple times (duplicated) into buffer table
- crash when importing cookbook with same source and target dfasdl
- cookbooks not completely deleted from database
- prevent deletion of cookbooks used by transformation configurations
- no work history shown on dashboard if no agent connected
- work history not loaded correctly for multiple transformations
- triggering transformations by other transformations starts wrong transformation

## 1.8.0 (2016-06-22)

### Added

- collaboration files
    - [AUTHORS.md](AUTHORS.md)
    - this CHANGELOG file
    - [CONTRIBUTING.md](CONTRIBUTING.md)
    - [LICENSE](LICENSE)
- show previous version of DFASDL including diff
- better error handling of frontend errors
- fetch logs from agent upon request

### Changed

- disable logging to database
- code cleanup and preparations for upgrading Play Framework to 2.5
- several ui fixes and cleanups
- switch versioning to sbt-git

### Fixed

- editor not working correctly in Firefox
- exception of database logging adapter

## 1.7.0 (2016-03-03)

### Added

- loading indicator in editor

### Changed

- allow `xml-element-name` on all elements
- decreased interval of checking for server connection
- name of auto generated DFASDL changed

### Fixed

- mapping keys not saved
- crash when importing huge cookbook
- broken json when exporting huge cookbook
- cookbook overview page too slow
- slow performance in editor

## 1.6.0 (2016-01-21)

### Changed

- include javascript libraries via webjars if possible
- update Play Framework to 2.4.6

### Fixed

- check for maximum allowed transformation configurations broken
- display errors if multiple sequences have the same id
- display errors if multiple DFASDLs have the same id
- importing cookbooks with already existing resource names
- pagination of log messages wrong
- `NullPointerException` when creating connection information

## 1.5.0 (2015-11-30)

### Added

- progress page for DFASDL extraction

### Fixed

- missing transformers
- dropdown for transformer options

## 1.4.2 (2015-10-13)

### Added

- editor tooltips showing transformations and mappings
- highlight currently selected node in editor
- easier creation of `AllToAll` mappings in editor

### Changed

- draw mapping lines "behind" other editor elements

### Fixed

- missing ids in editor tooltips
- long element names not wrapped
- unable to delete mappings

## 1.4.1 (2015-10-12)

### Added

- show transformers on the left side of the editor

### Changed

- shortened element ids to ease readability
- more compact editor design

### Fixed

- configuration for atomic transformers not saved correctly
- editor panes drawed multiple times

## 1.4.0 (2015-09-29)

### Added

- show memory of agent
- mapping visibility functionality in editor

### Changed

- update Play Framework
- paging for last transformations
- paging for logs

## 1.3.0 (2015-08-27)

### Added

- chained executing of transformation configurations via triggers

### Changed

- update to Play Framework to 2.4
- update Akka Quartz Scheduler
- form for triggers

### Fixed

- actors crash upon shutdown
- logs not displayed correctly
- exception when creating a cronjob


## 1.2.0 (2015-08-03)

### Added

- show new transformers in editor
- support new DFASDL attributes in editor

## 1.1.1 (2015-07-14)

- no significant changes

## 1.1.0 (2015-06-29)

### Added

- button for "new DFASDL"
- output license information
- timeout for DFASDL generation
- links to DFASDL from cookbook
- show new transformers in editor
- password confirmation field in setup form

### Changed

- no whitespace in dfasdl names and other elements
- overview pages for cronjobs and triggers
- lots of ui changes

### Fixed

- hard coded url in validate
- `NoSuchElementException` in `sourceConnectionResources`
- crash on empty input strings in transformation configuration
- minimum password length hint not displayed correctly
- 403 error upon username change
- cronjobs and triggers not executed
- prevent deleting admin group possible
- prevent deleting of last admin account
- prevent cookbooks having equal names
- `ClassCastException` in `TransformationError`

## 1.0.0 (2015-06-01)

Initial release.
