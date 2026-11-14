# Built by package.sh. The app ships its own trimmed Java runtime in /opt/subnet-calculator, so it
# needs no Java on the system and must not advertise or depend on the runtime's private libraries.
%global debug_package %{nil}
%global __os_install_post %{nil}
%global __arch_install_post %{nil}
%define _build_id_links none
%global __provides_exclude_from ^/opt/subnet-calculator/.*$
%global __requires_exclude ^(@BUNDLED_LIBS@)\\(

Name:           subnet-calculator
Version:        @VERSION@
Release:        1%{?dist}
Summary:        IPv4 subnet calculator, VLSM planner and Cisco IOS config generator
License:        MIT
URL:            https://github.com/digitaladministrator/subnet-calculator
Source0:        %{name}-%{version}.tar.gz
ExclusiveArch:  x86_64
Recommends:     google-noto-sans-fonts
Recommends:     google-noto-sans-mono-fonts

%description
A fast desktop IPv4 subnet calculator: network details, equal splits, VLSM planning, route
summarization, range-to-CIDR and a Cisco IOS router and switch config generator. Includes its own
Java runtime.

%prep
%setup -q -n %{name}

%install
mkdir -p %{buildroot}/opt/%{name} %{buildroot}%{_bindir} %{buildroot}%{_datadir}/applications \
         %{buildroot}%{_datadir}/icons/hicolor/scalable/apps
cp -a bin lib runtime %{buildroot}/opt/%{name}/
touch %{buildroot}/opt/%{name}/lib/%{name}.jsa
ln -s ../../opt/%{name}/bin/%{name} %{buildroot}%{_bindir}/%{name}
install -m 644 share/%{name}.svg %{buildroot}%{_datadir}/icons/hicolor/scalable/apps/%{name}.svg
cat > %{buildroot}%{_datadir}/applications/%{name}.desktop <<DESKTOP
[Desktop Entry]
Type=Application
Name=Subnet Calculator
GenericName=IPv4 Subnet Calculator
Comment=IPv4 subnets, VLSM plans, route summaries and Cisco IOS configs
Exec=%{name}
Icon=%{name}
Terminal=false
Categories=Network;
Keywords=subnet;cidr;ip;ipv4;netmask;vlsm;cisco;network;
StartupWMClass=subnetcalc-App
DESKTOP

%post
# Build the startup cache for the installed paths; the app works without it.
/opt/%{name}/bin/%{name} --build-cache >/dev/null 2>&1 || :

%files
%license LICENSE
%dir /opt/%{name}
/opt/%{name}/bin
/opt/%{name}/runtime
%dir /opt/%{name}/lib
/opt/%{name}/lib/%{name}.jar
/opt/%{name}/lib/%{name}.classlist
%ghost %attr(0644, root, root) /opt/%{name}/lib/%{name}.jsa
%{_bindir}/%{name}
%{_datadir}/applications/%{name}.desktop
%{_datadir}/icons/hicolor/scalable/apps/%{name}.svg

%changelog
* @DATE@ digitaladministrator - @VERSION@-1
- See CHANGELOG.md in the source repository.
