# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com)
and this project adheres
to [Semantic Versioning](http://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.8] — 2023-06-18

### Added

- Further documentation on included check function modules.
- A function to retrieve check name from check.
- A function to retrieve result status from result.

## Changed

- Upgrade to latest versions of all dependencies.

## [0.1.7] — 2021-10-30

### Added

- A data source check function module.
- An HTTP endpoint check function module.
- Publishing of an aggregate jar of all module jars as `io.logicblocks/salutem`.

### Fixed

- Use correct SCM details in sub module poms.

### Changed

- Change wording of multiple sections in Getting Started guide.

## [0.1.6] — 2021-09-24

### Changed

- Update Getting Started guide with details of asynchronous resolution.

## [0.1.5] — 2021-09-23

### Added

- An asynchronous variant of resolve-check.
- An asynchronous version of resolve-checks.
- Include evaluation duration in results.
- Documentation for checks/attempt.
- Documentation to maintenance processes.

### Changed

- Parallelise resolve-checks.
- Namespace salutem specific entries in checks and results.
- Update documentation and expose more signatures in core namespace.

## [0.1.4] — 2021-09-17

### Changed

- Update Getting Started guide with list of events logged by maintenance
  pipeline.

## [0.1.3] — 2021-09-17

### Added

- Create first pass of Getting Started guide.
- Allow asynchronous evaluation of checks.
- Tests for salutem.checks/attempt.
- Catch exceptions from check functions and convert to unhealthy result.
- Ensure only one evaluation of each check takes place at a time in maintenance
  pipeline.
- Add performance tests of the maintenance pipeline and registry.

### Changed

- Rename `:ttl` to `:time-to-re-evaluation` to be more clear on what it is used
  for. `:ttl` is still supported but consumers should adopt
  `:time-to-re-evaluation` instead as `:ttl` may be removed in a future release.

## [0.1.2] — Never released

## [0.1.1] — 2021-08-26

### Fixed

- Fix issue where `cartus.null` was incorrectly included with test scope
  causing consumers to have to install manually for `salutem` to work correctly.

## [0.1.0] — 2021-08-24

### Added

- Create _CHANGELOG.md_.

## 0.1.0-RC19 — 2021-08-24

Released without _CHANGELOG.md_.


[0.1.0]: https://github.com/logicblocks/salutem/compare/0.1.0-RC19...0.1.0

[0.1.1]: https://github.com/logicblocks/salutem/compare/0.1.0...0.1.1

[0.1.3]: https://github.com/logicblocks/salutem/compare/0.1.1...0.1.3

[0.1.4]: https://github.com/logicblocks/salutem/compare/0.1.3...0.1.4

[0.1.5]: https://github.com/logicblocks/salutem/compare/0.1.4...0.1.5

[0.1.6]: https://github.com/logicblocks/salutem/compare/0.1.5...0.1.6

[0.1.7]: https://github.com/logicblocks/salutem/compare/0.1.6...0.1.7

[0.1.8]: https://github.com/logicblocks/salutem/compare/0.1.7...0.1.8
[Unreleased]: https://github.com/logicblocks/salutem/compare/0.1.8...HEAD
