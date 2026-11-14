# Changelog

All notable changes to Subnet Calculator. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow
[Semantic Versioning](https://semver.org/).

## [1.3.1] - 2026-11-14

### Changed
- **Interfaces are set per device.** The config page now has separate Router and Switch sections,
  each with its own hostname, port type (FastEthernet / GigabitEthernet) and numbering (router
  `0/0` or `0/0/0`; switch `0/1` or `1/0/1`). The fixed "access port prefix" field is replaced by
  the switch's port type and numbering.
- **Layer 3 is a switch setting.** The switch is now None, Layer 2 or Layer 3. The router only offers
  its own inter-VLAN choices (router on a stick or a port per subnet), which hide when the switch
  does the routing. Settings from 1.3.0 carry over.
- A port per subnet with a layer 2 switch now also produces the switch's VLANs and access ports.

### Added
- **Inter-VLAN port choice** on the router: the trunk port (router on a stick), the first LAN port
  (port per subnet, numbered upward) or the port facing a layer 3 switch. Automatic ports skip any
  port you pick, and a port used for two things is reported.
- **Security and remote access for the router and the switch separately:** enable secret, console
  password, local user (`privilege 15 secret`), Telnet, SSH or both (`ip domain-name`, RSA key of
  1024 or 2048 bits, `ip ssh version 2`), VTY lines 0–4 or 0–15, idle timeout, `service
  password-encryption` and a MOTD banner. Passwords are not saved between runs.
- **Switch management interface** for layer 2 switches: a management VLAN (automatic picks a VLAN
  named like "Management") with an address and `ip default-gateway`, which remote access to the
  switch needs. The address is excluded from DHCP.
- Checks for SSH without a local user, Telnet without a password, passwords with spaces or `?`
  (which would break a pasted config), router on a stick without a switch, and the switch uplink
  overlapping its access ports.
- Tests for the new options (172 checks, up from 150).

## [1.3.0] - 2026-11-14

### Added
- **IOS config page** (fourth tab, Ctrl+4): generates Cisco IOS router and switch configurations
  from the VLSM plan, updating live, ready to paste into Packet Tracer or a real device.
  - LAN design: router on a stick (802.1Q subinterfaces, switch VLANs, access ports, trunk),
    one router port per subnet, or a layer 3 switch (SVIs, `ip routing`, routed link to the router).
  - Port type FastEthernet or GigabitEthernet, `0/0` or `0/0/0` numbering, gateway on the first or
    last usable address, and 1–2 host subnets as point-to-point links on automatic or named
    interfaces (e.g. `Serial0/1/0`, numbered upward).
  - VLAN first ID and step, native VLAN, access ports per VLAN, access port prefix, uplink port.
  - DHCP pools with the gateway excluded, DNS server, domain name and addresses kept for static devices.
  - OSPF (process, area, router ID, wildcard-mask networks, passive LAN interfaces, default route
    origination), automatic static routes for the layer 3 design without OSPF, and extra static routes.
  - Internet uplink (static or DHCP) with a default route, and NAT: PAT on the uplink, an address
    pool with or without overload, static NAT and TCP/UDP port forwards.
  - Output options: `enable` … `write memory` wrapper, lab extras, section comments.
  - Copy or save (`.txt`) each device's config; all options are remembered between runs.
- Tests for the config generator across all three designs, NAT modes and invalid input
  (150 checks, up from 110).

### Changed
- The save dialog is shared between CSV export and config files.
- Default window size is 1100 × 820 to fit the fourth tab and the config page.

## [1.2.1] - 2026-11-14

### Fixed
- **Editing keys did nothing in text fields.** Backspace, Delete, the arrow keys, Home/End,
  Shift-selection and Ctrl+A/C/X/V had no effect; only typing new characters worked. The app's custom
  look and feel is built on Swing's `BasicLookAndFeel`, which defines no keyboard bindings, so none
  were installed. It now uses the standard cross-platform bindings, which also restores arrow-key
  navigation in tables and menus. The bug was present since 1.0.0.

### Added
- Regression tests that check the editing keys are bound in fields, text areas and tables
  (110 checks, up from 93).

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

[1.3.1]: https://github.com/digitaladministrator/subnet-calculator/compare/v1.3.0...v1.3.1
[1.3.0]: https://github.com/digitaladministrator/subnet-calculator/compare/v1.2.1...v1.3.0
[1.2.1]: https://github.com/digitaladministrator/subnet-calculator/compare/v1.2.0...v1.2.1
[1.2.0]: https://github.com/digitaladministrator/subnet-calculator/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/digitaladministrator/subnet-calculator/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/digitaladministrator/subnet-calculator/releases/tag/v1.0.0
