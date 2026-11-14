# Changelog

All notable changes to Subnet Calculator. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow
[Semantic Versioning](https://semver.org/).

## [1.1.0] - 2026-11-14

### Added
- **VLSM planner page.** Enter a network and a list of named host requirements. The planner gives
  each one the smallest block that fits, places them largest first with no gaps, lists the leftover
  free space as CIDR blocks, and reports when the requirements don't fit.
- **/31 and /32 links switch** in the planner, for RFC 3021 point-to-point links and single-host
  routes. When it's off, 1- and 2-host needs get a /30.
- **Export CSV…** on both pages. The calculator exports its subnet split and the planner exports its
  plan. Files follow RFC 4180, with separate columns for network, prefix, netmask, first/last host,
  broadcast and host counts. Uses KDE's save dialog when `kdialog` is available, and exports run on a
  background thread so large splits (up to 1,048,576 rows) don't freeze the window.
- **Page tabs** (Calculator / VLSM planner) in the header. The last page used is remembered.
- **Version number** shown next to the title, read from the jar manifest, plus a `VERSION` file.
- Tests for the VLSM planner and CSV formatting (73 checks in total, up from 46).

### Changed
- Table headers now line up with their columns (numbers right-aligned, text left-aligned) instead
  of being centred.
- The default window is wider (960 × 780) so the planner's columns fit without truncation.
- The shared look and feel and widgets moved from `App` into `Ui`. The calculator page is now
  `CalculatorView`, and `App` is a small window shell.

### Fixed
- `install.sh` now deletes the previous startup cache when updating, so the first launch after an
  update no longer prints class-data-sharing warnings.

## [1.0.0] - 2026-11-14

### Added
- First release: IPv4 calculator with live results (network, broadcast, host range, masks, class,
  address type, hex and binary views), an even subnet split table, a plain-text summary copied to the
  clipboard, a dark/light switch matched to the KDE colour scheme, and a per-user installer with an
  application-menu entry.

[1.1.0]: https://github.com/digitaladministrator/subnet-calculator/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/digitaladministrator/subnet-calculator/releases/tag/v1.0.0
