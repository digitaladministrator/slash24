# Changelog

All notable changes to Subnet Calculator. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow
[Semantic Versioning](https://semver.org/).

## [1.2.0] - 2026-11-14

### Added
- **Route tools page** (third tab):
  - *Summarize networks*: the smallest single summary route covering a list of networks, how many
    extra addresses it includes, and the exact aggregate (fewest blocks covering only the list, with
    overlaps and neighbours merged). A bare address counts as a /32.
  - *Range to CIDR*: the fewest CIDR blocks covering exactly a first-to-last address range.
- **"Contains?" check** on the calculator: whether an address is inside the network, its position
  (host number, or the network or broadcast address), and which split subnet it falls in. That table
  row is selected and scrolled into view.
- **Copy table** on every page: the table goes to the clipboard as tab-separated text, ready to paste
  into a spreadsheet (up to 100,000 rows).
- **Match system** switch: follows the Plasma colour scheme and accent colour live by watching
  `kdeglobals` (inotify, no polling). The Dark mode switch is used when Match system is off.
  Match system is the default for new installs; an earlier dark/light choice is kept on upgrade.
- **Recent networks**: the last 10 valid networks on the Calculator and VLSM pages, from a Recent
  button or the Down arrow in the field, with Clear history.
- **Keyboard shortcuts**: Ctrl+L (focus input), Ctrl+Shift+C (copy), Ctrl+D (dark/light),
  Ctrl+1/2/3 (switch page).
- Tests for summarization, range splitting and the "Contains?" wording (93 checks, up from 73).

### Changed
- Input errors now appear once typing pauses (about 0.8 s), so half-typed input such as `192.168.`
  is no longer flagged red immediately. An error that is already showing updates straight away.
- Default window size is 1000 × 800 to fit the new header controls.

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

[1.2.0]: https://github.com/digitaladministrator/subnet-calculator/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/digitaladministrator/subnet-calculator/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/digitaladministrator/subnet-calculator/releases/tag/v1.0.0
