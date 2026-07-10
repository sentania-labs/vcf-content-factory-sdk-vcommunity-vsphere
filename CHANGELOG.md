# Changelog

## build-4 — corrected dev-build version convention `99.x` → `0.0.0.x` (2026-06-25)

`fix(framework): correct hand-build version line to 0.0.0.x so dev previews always sort below real releases`

Hand-built **dev preview** rebuild — version `0.0.0.4`. No adapter source changed.

The prior build-3 marked dev/hand builds with major version `99` (`99.0.0.x`).
That convention is **wrong and dangerous**: a `99.x` pak makes a future real
`1.0.0.x` release look like a *downgrade*, so VCF Ops refuses the upgrade.
Corrected convention: dev/hand builds carry major.minor.patch = `0.0.0` and only
advance the 4th field (build_number), so they always sort **below** any real
release. This build is therefore `0.0.0.4` (was erroneously `99.0.0.3`).

Re-bundles the localization-fixed framework base jar `vcfcf-adapter-base.jar`
(sha256 `4eabad523a30ed547b5aa8987b26d1517dc9d7c89c87a6217ac642ebb3734c53`)
carrying the `onDescribe()` localization fix (`AdapterDescribe.make(String)` swap).

## build-3 — dev preview build on localization-fixed base jar (2026-06-25)

`fix(framework): rebuild vcommunity-vsphere pak to bundle the onDescribe()-localization-fixed base jar`

Hand-built **dev preview** rebuild — version `99.0.0.3`. The `99.x` major is the
local hand-built marker that keeps these visually distinct from CI/release builds
(which stay on the `1.0.0.x` line); only the 4th field (build_number) advances.

No adapter source changed. This rebuild re-bundles the freshly rebuilt framework
base jar `vcfcf-adapter-base.jar` (sha256 `4eabad523a30ed547b5aa8987b26d1517dc9d7c89c87a6217ac642ebb3734c53`,
60252 bytes, Jun 24 18:28) carrying the `onDescribe()` localization fix
(`AdapterDescribe.make(String)` swap). Every SDK pak bundles this jar, so a rebuild
is the only way to ship the fix into the pak.

Note: the prior `1.0.0.2` pak on disk already bundled this same fixed jar; the
genuinely-old pre-fix base jar (sha256 `0e873aec…`, 60414 bytes, Jun 10) was last
bundled in `1.0.0.1`. This build is verified to bundle `4eabad52…` and to differ
from the `0e873aec…` pre-fix jar.

## build-2 — fix Accounts UI raw-key localization (2026-06-24)

`fix(adapter): drop non-standard <int>.description bundle keys so describe nameKeys resolve`

The Accounts / adapter-config UI rendered raw describe keys instead of friendly
labels — the solution name showed as `vcfcf_vcommunity_vsphere` and connection
params as `host`, `port`, `allowInsecure`, the four config-file keys, etc.
(investigation `context/investigations/vcommunity-vsphere-localization-2026-06-24.md`).
Root cause (prime suspect): `resources/resources.properties` carried seven
non-standard `<int>.description=` help-text keys (nameKeys 6/8/9/10/11/14/15)
that no describe.xml attribute referenced and that no known-good control pak
(compliance/synology/unifi) uses — suspected of triggering whole-bundle
rejection by the describe/Dictionary loader, so every nameKey (incl. 1/5, the
names) fell back to its raw key string.

**Fix:** removed all seven `<int>.description=` keys, leaving only the standard
`<int>=label` shape the control paks use. `describe.xml` was already free of any
`descriptionKey`/`<Description>` reference to these keys, so it is untouched and
fully consistent. The field (i) tooltips were **dropped, not re-expressed**: the
describe XSD (`describeSchema.xsd`) allows only an `<enum>` child on
`ResourceIdentifier`/`CredentialField` — there is NO schema-valid `<Description>`
element or `descriptionKey` attribute for connection params (the spec's
`<Description nameKey>` is exclusive to `<Recommendation>`). Re-adding the help
text would itself be schema-invalid and risk the same bundle problem, so the
tooltips are parked pending a proper, schema-sanctioned mechanism.

**Confirmation status:** the bundle-rejection mechanism is the prime suspect but
is NOT statically proven — both "reject whole bundle" and "ignore the dotted
keys" are consistent with a structurally valid `.properties` file. Final
confirmation is install-on-devel + verifying labels resolve in the Accounts UI,
which happens after `sdk-adapter-reviewer` and before any install.

## build-1 — fork of unified vcommunity → vcommunity-vsphere (2026-06-23)

`feat(adapter): fork the unified vCommunity adapter into the vcommunity-vsphere pak`

STEP 2 of the vCommunity vsphere/os split
(`designs/managementpacks/vcommunity-three-adapter-split.md`). Forked the unified
`vcfcf_vcommunity` adapter (build 11) into this fresh-lineage
`vcfcf_vcommunity_vsphere` pak and stripped the in-guest guest-ops surface (now
owned by the `vcommunity-os` pak, STEP 1). Fresh build numbering starts at 1.

**Kept (the vSphere pak's surface — the bulk):**
- `ClusterCollector.java` (HA/DRS/EVC/DRS Score), `HostCollector.java` (advanced
  system settings, packages/VIBs, install date, licensing, NIC uplinks), and the
  vSphere-side branch of `VmCollector` (snapshot count, VM options, advanced
  parameters, SCSI controllers).
- **The passive VMware-Tools `Guest OS|Operating System|*` keys** —
  `VCommunityVSphereClient.vmGuestOsInfo(...)` AND its live caller in
  `VmCollector.collectConfig` that pushes `vCommunity|Guest OS|Operating System|*`
  (vim25 `guest.detailedData`, no Windows credential). While `vcommunity-os` is
  shelved, this pak is the SOLE writer of these six keys (design OPEN-A refinement).
- The plumbing for its OWN vCenter session + stitch: `VCommunityVSphereClient`,
  `VCommunityStitcher`, `SolutionConfigStore`, the `SuiteApiStitcher` usage
  (deliberate duplication per design §2 — not shared with the os pak).
- The vCenter credential kind (`vsphere_user`, user/password).
- The four vSphere SolutionConfig XMLs (`esxi_advanced_system_settings.xml`,
  `esxi_packages.xml`, `vm_advanced_parameters.xml`, `vm_options.xml`) and their
  adapter-instance config-file fields.
- All 37 super metrics, the vSphere views, 12 dashboards, and the
  `ESXi Host NIC Disconnected` symptom + alert.
- **The build-2 `VMEntityVCID` stitch-scoping fix — retained verbatim**, wired
  from the live SOAP session via `stitcher.setOwningVcUuid(vsphere.getVCenterInstanceUuid())`
  (`lessons/stitch-moid-not-unique-across-vcenters.md`, design Risk #2).

**Stripped (guest-OS — belongs to vcommunity-os):**
- `GuestOpsClient.java` entirely.
- The guest-ops branch of `VmCollector` (Windows services, Windows event logs,
  in-guest CSV OS-info via `getWindowsOSInformation.ps1`) and the build-9/10
  guest-ops diagnostics (`guestops_ready`/`guestops_skips`/`guestops_last_error`
  anchor properties and their `Result` fields).
- The Windows credential fields (`winUser`/`winPass`), the `WindowsMonitoring`
  enum and its two describe.xml enums (`serviceMonitoring`/`winEventMonitoring`).
- The two Windows SolutionConfig XMLs (`windows_service_list.xml`,
  `windows_event_list.xml`) and their config-file fields.
- The three guest-ops `.ps1` scripts and the now-empty `profiles/scripts` dir.
- Windows-only content: the `Windows Service Down` symptom + alert and the
  `Windows Services vCommunity` view.

**Re-identity:** adapter kind `vcfcf_vcommunity` → `vcfcf_vcommunity_vsphere`; MP
display name → `VCF Content Factory vCommunity vSphere`; describe.xml,
adapter.yaml, icons.yaml, resources.properties, README/REFERENCE/docs all updated.
Fresh git lineage (initial commit, not a continuation of the unified history).

## Carried gaps (from the unified pak)

- **TOOLSET GAP #1 — foreign-resource event push.** Host install-date read
  failures ship as alertable properties, not real events (Suite API facade has no
  event endpoint). Real events → **v1.1**.
