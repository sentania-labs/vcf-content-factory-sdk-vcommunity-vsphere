# VCF Content Factory vCommunity vSphere — Reference

vSphere/vCenter fork of the unified vCommunity Java SDK adapter (native Java SDK
rewrite of `vmbro/VCF-Operations-vCommunity`, Onur Yuzseven, CC-licensed).
Adapter kind `vcfcf_vcommunity_vsphere`. One of three paks the unified
`vcfcf_vcommunity` adapter was split into per
`designs/managementpacks/vcommunity-three-adapter-split.md`; this pak owns the
bulk vSphere collection surface. The in-guest Windows guest-ops surface belongs
to the `vcommunity-os` pak.

## Adapter

| Field | Value |
|---|---|
| Adapter Kind | `vcfcf_vcommunity_vsphere` |
| Tier | 2 (Java SDK) |
| Monitoring Interval | 5 minutes |
| License Required | No |

### Credentials (ONE credential kind — vCenter only)

**vCenter Credential** (`vsphere_user`, required). The vSphere pak has NO Windows
guest credential: a vSphere-only admin sees only vCenter user/password (design §4
RBAC/UX win).

| Field | Key | Type | Required |
|---|---|---|---|
| vCenter User Name | `user` | string | Yes |
| vCenter Password | `password` | string (masked) | Yes |

The type=7 adapter instance binds this single kind via
`credentialKind="vsphere_user"`.

### Connection Settings

| Field | Key | Default | Required |
|---|---|---|---|
| vCenter Server | `host` | — | Yes |
| ESXi Advanced System Settings Config File | `esxi_adv_settings_config_file` | esxi_advanced_system_settings | No |
| ESXi Software Packages Config File | `esxi_vib_driver_config_file` | esxi_packages | No |
| VM Advanced Parameters Config File | `vm_adv_settings_config_file` | vm_advanced_parameters | No |
| VM Options Config File | `vm_configuration_config_file` | vm_options | No |
| Port | `port` | 443 | No |
| Allow Insecure SSL | `allowInsecure` | false | No |

The four `*_config_file` fields hold the NAME (no path, no `.xml`) of a
check-list file in the VCF Ops central configuration-file store under
`SolutionConfig/`. The bundled defaults ship in `content/files/solutionconfig/`
and import into the central store at install; everything in them is commented
out by design, so each gated collector emits nothing until an admin uncomments
entries centrally.

---

## Declared Object Type

### vCommunity World (`vCommunityWorld`, type=1)

Synthetic INTERNAL collection anchor (operability only). **Identifier**:
`world_id`.

#### Summary

| Key | Type | Meaning |
|---|---|---|
| `clusters_stitched` | metric | clusters matched + pushed this cycle |
| `hosts_stitched` | metric | hosts matched + pushed this cycle |
| `vms_stitched` | metric | VMs matched + pushed this cycle |
| `last_scan_timestamp` | property | ISO timestamp of the last cycle |
| `config_file_status` | property | per-file fetched/parsed check counts + degradation notices |
| `status` | property | `OK` / stitcher-unavailable notice |

---

## Foreign-resource keys (pushed via Suite API, NOT in describe.xml)

Every key below is pushed onto the *existing* VMWARE resource (owned by the
VMWARE adapter) — they are intentionally not declared here, exactly as the
compliance adapter pushes onto VMWARE HostSystem. The namespace is `vCommunity|`
verbatim from the original (RULE-002; every key traced to a source line in
`designs/managementpacks/vcommunity-sdk.md`). Stitch identity is scoped to the
owning vCenter Instance UUID (the build-2 `VMEntityVCID` fix —
`lessons/stitch-moid-not-unique-across-vcenters.md`).

### ClusterComputeResource

`vCommunity|Cluster Configuration|vSphere HA|{Host Monitoring, Response \ Host
Isolation, Response \ Default VM Restart Priority, Response \ Datastore APD,
Response \ Datastore PDL, VM Monitoring, Heartbeat Datastore}` (properties; push
`"null"` when HA disabled) · `…|DRS|{Proactive DRS, Scale Descendants Shares,
CPU Over-Commitment}` (properties) · `…|DRS|DRS Score` (metric; 0 when DRS
disabled) · `…|EVC|{Enabled, Mode}` (properties).

### HostSystem (connected hosts only)

`vCommunity|Configuration|Advanced System Settings|{key}` (filtered to the
central check-list) · `…|Packages:{name}|{Package Name, Package Version,
Acceptance Level, Maintenance Mode Required, Package Summary, Package Type,
Package Vendor}` (filtered) · `…|Install Date|UTC` (or `…|Install Date|Read
Error` on read failure — TOOLSET GAP #1 degradation) ·
`vCommunity|Licensing:{name}|{Name, License Key, License Expiration Date,
Edition Key}` (properties) + `…|Remaining Days` (metric) ·
`vCommunity|Network|Device:{device}|{Device Name, Driver Version, Firmware
Version, Status}` (properties).

### VirtualMachine

`vCommunity|Snapshot|Count` (metric) · `vCommunity|Options|{configPath}`
(filtered to the central check-list) · `vCommunity|Configuration|Advanced
Parameters|{key}` (filtered) · `vCommunity|Configuration|SCSI Controllers|Count`
(metric) + `…|SCSI Controllers:{bus}|Type` (property) ·
`vCommunity|Guest OS|Operating System|{OS Name, OS Version, OS BuildNumber, OS
Architecture, OS Last Boot Up Time, OS Release ID}` — the **passive VMware-Tools
path** (vim25 `guest.detailedData`, no Windows credential, every Tools-running
guest). While the `vcommunity-os` pak is shelved, this pak is the SOLE writer of
these six keys (design 2026-06-23 OPEN-A refinement). The in-guest CSV OS-info,
`vCommunity|Guest OS|Services:*`, and the `Last Event` degraded keys belong to
the `vcommunity-os` pak and are NOT emitted here.

---

## TOOLSET GAP #1 — foreign-resource event push

The original emits host install-date read failures (and, in the os pak, Windows
event-log entries) as foreign-resource **events**. The factory `SuiteApiStitcher`
facade exposes only `pushProperties` / `pushStats` — there is no foreign-resource
event/alert push endpoint wired in the framework. Per the design's accepted
staged plan, host install-date failures are degraded this release to **alertable
properties** (visible, symptom-/alert-able, never silently dropped). Real
foreign-resource events are a **v1.1** deliverable once a clean Suite API
event-push path is proven (route to `tooling` to add `SuiteApiStitcher.pushEvents`).
