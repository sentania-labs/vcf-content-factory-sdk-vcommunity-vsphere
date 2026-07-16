# VCF Content Factory vCommunity vSphere

A **Tier 2 Java SDK management-pack adapter** for VCF Operations — the
**vSphere/vCenter fork** of the unified vCommunity adapter (native Java SDK
rewrite of [`vmbro/VCF-Operations-vCommunity`](https://github.com/vmbro/VCF-Operations-vCommunity),
Onur Yuzseven, CC-licensed). One of three paks the unified `vcfcf_vcommunity`
adapter was split into per
[`vcommunity-three-adapter-split.md`](../../../designs/managementpacks/vcommunity-three-adapter-split.md);
this pak (`vcfcf_vcommunity_vsphere`) owns the **bulk vSphere collection
surface** read over the vim25 vCenter session. The in-guest Windows guest-ops
surface belongs to the [`vcommunity-os`](https://github.com/sentania-labs/vcf-content-factory-sdk-vcommunity-os)
pak. The adapter runs natively in the collector like the compliance reference
adapter.

## What it does

Reads vCenter over vim25 SOAP and pushes the `vCommunity|` property/metric
namespace onto **existing** VMWARE resources (ARIA_OPS-style stitching — no new
object types):

- **ClusterComputeResource** — vSphere HA / DRS / EVC configuration + DRS Score.
- **HostSystem** — advanced system settings, VIB packages, install date,
  licensing (+ Remaining Days), physical NIC uplinks.
- **VirtualMachine** — VM options, advanced parameters, SCSI controllers,
  recursive snapshot count, and the **passive VMware-Tools** `Guest OS|Operating
  System|*` path (vim25 `guest.detailedData`, no Windows credential, every
  Tools-running guest). While the `vcommunity-os` pak is shelved, this pak is the
  SOLE writer of those six OS keys (design 2026-06-23 OPEN-A refinement). The
  in-guest CSV OS-info, Windows services, and event logs belong to the
  `vcommunity-os` pak and are NOT collected here.

A single synthetic `vCommunityWorld` resource carries per-cycle operability
metrics (clusters/hosts/VMs stitched, config-file status).

See [`REFERENCE.md`](REFERENCE.md) for the full key catalog and
[`docs/`](docs/README.md) for the inventory-tree docset.

### Bundled content

- **109 views** (`content/views/`) — most back the reports below;
  standalone highlights are `VM Network Top Talkers`, `nfnic VIB Vendor
  Distribution`, `VM Memory Allocation Trend`, and `Distributed Port
  Groups`. Full list in [`REFERENCE.md`](REFERENCE.md#bundled-views-contentviews-109-total).
- **11 CSV-export "VOA" reports** (`content/reports/`) — one table per
  resource kind (Datastores, Hosts, VMs, vCenter, Clusters, Data Centers,
  Namespaces, Supervisor, Pods, Distributed Switch, Distributed Port
  Groups), designed to be run together and combined in a spreadsheet. Full
  list in [`REFERENCE.md`](REFERENCE.md#bundled-reports-contentreports-11-total).
- **Deferred, out of scope for this pak:** the vendor's 5 PDF "VOA"
  reports and the 34-dashboard "Input dashboards" template
  (`dashboard-author`-scope follow-up); the Windows/in-guest-surface
  views belong to the `vcommunity-os` pak.

## Relationship to the unified pak (fresh-lineage fork)

This is a **distinct adapter kind** (`vcfcf_vcommunity_vsphere`), forked from the
unified `vcfcf_vcommunity` adapter with the Windows guest-ops surface stripped
out. It is a fresh git lineage, not a continuation of the unified history.

### Migration runbook

1. **Uninstall the unified** `vcfcf_vcommunity` pak. Its adapter instances and
   credentials cascade away on uninstall
   (`lessons/pak-uninstall-cascades-credentials.md`).
2. **Install this pak** (`vcfcf_vcommunity_vsphere`) — and `vcommunity-os` too if
   Windows guest monitoring is wanted.
3. **Recreate adapter instances and credentials** against the new kind — there
   is no instance/credential carry-over across a kind change.

Because this adapter writes the *same* `vCommunity|` keys onto the *same*
VMware-owned resource UUIDs, historical metric/property series remain
mechanically continuous through the migration. This is a happy side effect of
key-namespace continuity, **not** a supported upgrade guarantee.

## Configuration

### Credentials (vCenter only)

A single credential kind (`vsphere_user`, "vCenter Credential") with two fields —
`user` / `password`, both required. The vSphere pak has **no Windows guest
credential**: a vSphere-only admin sees only vCenter user/password (the RBAC/UX
win the split exists to deliver). Windows guest monitoring is opted into by
installing the separate `vcommunity-os` pak.

### Central check-list files

Collection of ESXi advanced settings / VIBs / VM params / VM options is gated by
four XML check-lists in the VCF Ops **central configuration-file store**
(`Administration → Configuration Files`, path `SolutionConfig/`). The pak ships
byte-identical defaults under `content/files/solutionconfig/`; they import into
the central store at install with **everything commented out**, so each gated
collector emits nothing until an admin uncomments entries. The four adapter-
instance `*_config_file` fields hold the file NAME (no path, no `.xml`); point
one at a renamed central file to customize without editing the bundled default.

The adapter fetches the named files via the SDK-injected Suite API channel each
cycle and caches the last-good parse — a transient fetch failure degrades to the
previous cycle's lists (never silently to empty), and `test()` plus the
`vCommunityWorld` `config_file_status` property report per-file status.

## Post-install: policy enablement

Two things a fresh install does **not** do for you — both are one-time
per-policy edits in **Configure → Policies**, not adapter bugs:

- **Super metrics ship unactivated.** All ~30 bundled SMs are assigned to
  their resourceKinds but not enabled in any policy (matching the source
  pack's own behavior). Every SM-driven widget — `Cluster Performance`
  HealthCharts, `Clusters not Green`, and the rest — shows "No data" until
  you enable them. Edit the policy governing your vSphere objects →
  **Metrics and Properties** → filter to **Super Metrics** → enable the
  ones you use → save.
- **Two VMWARE HostSystem metrics needed by the Bad Network Packets chain
  are disabled by default:** `net|packetsRx_summation` and
  `net|packetsTx_summation`. The `ESXi Bad Network Packets` SM (and its
  roll-up into `vSphere Cluster Performance` / `vSphere Clusters not
  Green`) computes nothing until you also enable these two base attributes,
  in the same **Metrics and Properties** editor, on HostSystem.

Do both edits together — the base attributes and the super metrics that
consume them — before judging any of the above widgets as broken.

## Events (TOOLSET GAP #1)

The original emits host install-date read failures as foreign-resource
**events**. The factory Suite API facade exposes only property/stat push (no
foreign-resource event endpoint), so this release degrades them to **alertable
properties** (`vCommunity|Configuration|Install Date|Read Error`) — visible and
symptom-able, never silently dropped. Real foreign-resource events are a v1.1
deliverable once the push path is proven.

## Known gaps & roadmap

Documented gaps that close in later builds — nothing below is a silent
degradation; each is visible in the artifact and alertable or surfaced where it
matters.

### Carried into v1.1

- **Foreign-resource event push (TOOLSET GAP #1).** Host install-date read
  failures ship as alertable **properties** (`vCommunity|Configuration|Install
  Date|Read Error`) rather than real foreign-resource events, because the factory
  Suite API facade exposes only property/stat push (no event endpoint). Visible
  and symptom-able, never dropped. Real events land in v1.1 once the push path is
  proven. (See the Events section above.)

### RESOLVED — `instanced` attribute (DEF-008, closed)

Historical note. Against the `1.0.0.2` pak,
`src/vcfops_alerts/render.py::_add_condition_element` did not emit `instanced`
on `<Condition>` for `metric_static` / `property` conditions in the
`content/alertdefs/` / `content/symptomdefs/` XML the pak build produces —
the REST-sync wire path already carried it correctly. This would have
downgraded `ESXi Host NIC Disconnected` and the `ESXi Host License Expiring`
symptoms to exact-string key matching, defeating the license alert's design
point (matching any license name without hardcoding one). Root-caused as
factory defect DEF-008 and fixed in factory PR #46 (`sdk-buildkit` 1.0.7+).
As of build 10, the extracted pak's symptomdefs all carry `instanced="true"`
with correct `thresholdType`/`valueType`. See `REFERENCE.md` §
"RESOLVED — `instanced` attribute" and `knowledge/context/defects.md` DEF-008.

### EMPIRICAL-VERIFY at install

These behaviours are designed and code-complete but have **not** been verified
against a live appliance — confirm them during the first install:

- The credential dialog renders the vCenter Credential and accepts an instance.
- vim25 surfaces used beyond the compliance-proven set resolve on the target
  vCenter(s): `QueryAssignedLicenses`, `fetchSoftwarePackages` / `installDate`,
  and `EvcManager`.
- Suite API config-file fetch (`SolutionConfig/<name>.xml`) succeeds when the
  adapter runs on a **remote collector** / cloud proxy, not just the analytics
  node.

## Local dev build (preview)

The official `.pak` is built by CI on a tag. For a local preview the Broadcom SDK
jar (`vrops-adapters-sdk-2.2.jar`) is **not** shipped — supply it from your
appliance:

```sh
# Cheap loop first — exhaust this before building a pak:
python3 -m vcfops_managementpacks validate-sdk content/sdk-adapters/vcommunity-vsphere

# scp root@<appliance>:/usr/lib/vmware-vcops/common-lib/vrops-adapters-sdk-2.2.jar .
export VCFCF_SDK_JAR=/path/to/vrops-adapters-sdk-2.2.jar
python3 -m vcfops_managementpacks build-sdk content/sdk-adapters/vcommunity-vsphere -o dist
```

`vcfcf-adapter-base.jar` is provided by the builder; you do **not** commit it.

## CI release contract

The shippable `.pak` is built by CI, never on a laptop: commit + push to `main`,
then push a `vX.Y.Z` tag. The `build-pak-on-tag` workflow pulls the published
`sdk-buildkit`, fetches the private Broadcom SDK jar (`SDK_RUNTIME_TOKEN`
secret), builds deterministically, gates on `pak-compare`, and attaches the
`.pak` to the tag's GitHub Release. That Release asset **is** the release.

## C2 pak shape — no bundled jars

This pak never carries `vrops-adapters-sdk` or any Broadcom jar.
`vcfcf-adapter-base.jar` comes from the buildkit at build time;
`vrops-adapters-sdk-*.jar` is on the appliance classpath at runtime and supplied
to the compiler by the consumer. `.gitignore` ignores `lib/*.jar`.

## Building from source

You don't need this repo's CI or the VCF Content Factory checkout to
build the `.pak` — the toolchain is a portable tarball. You need:

- **JDK 11+** (`javac` + `jar` on PATH)
- **python3** with `pyyaml` (`python3 -m pip install pyyaml`)
- **The GitHub CLI** (`gh`) — used to download the build toolchain
  below. The factory repo is public, so no `gh auth login` is needed
  for the download (authenticate only if you hit anonymous API rate
  limits). No `gh`? See the `curl` alternative under step 1.
- **The Broadcom adapter SDK jar** (`vrops-adapters-sdk-2.2.jar`).
  This is a Broadcom build artifact with no public redistribution
  channel — it is **never** bundled in the toolchain or this repo.
  Get it from your own VCF Operations appliance:

  ```
  scp root@<appliance>:/usr/lib/vmware-vcops/common-lib/vrops-adapters-sdk-2.2.jar .
  ```

  (Also present at
  `/usr/lib/vmware-vcops/suite-api/WEB-INF/lib/vrops-adapters-sdk.jar`.
  Partners can pull it from the Broadcom TAP / partner SDK portal
  instead.)

Then, from the root of this repo:

```bash
# 1. Fetch the build toolchain (pin a full sdk-buildkit-vX.Y.Z tag for
#    reproducibility, or use the floating major sdk-buildkit-v1)
gh release download sdk-buildkit-v1 \
  --repo sentania-labs/vcf-content-factory \
  --pattern 'sdk-buildkit-*.tgz'
# No gh? The asset is public — fetch it with curl instead:
#   curl -sL https://github.com/sentania-labs/vcf-content-factory/releases/download/sdk-buildkit-v1/sdk-buildkit-v1.tgz -o sdk-buildkit-v1.tgz
tar xzf sdk-buildkit-*.tgz

# 2. Point the kit at your SDK jar and build
export VCFCF_SDK_JAR=/path/to/vrops-adapters-sdk-2.2.jar
python3 -m sdk_buildkit validate-sdk .   # cheap loop: compile-check
python3 -m sdk_buildkit build-sdk .      # emits the .pak
```

The kit carries everything else it needs (including the
`vcfcf-adapter-base.jar` framework runtime that ends up in the pak's
`lib/`). `validate-sdk` is the fast iteration loop; exhaust it before
building paks.

**Dev builds vs releases.** Anything you build this way is a *dev
build*. The **official** artifact for this repo is the one its own CI
builds and attaches to a GitHub Release when a `v*` tag is pushed —
deterministic, no developer machine in the path.

**If you fork this repo**, the CI workflow
(`.github/workflows/build-pak-on-tag.yml`) needs two adjustments
before your own `v*` tags will build:

1. **Runner**: it targets a `self-hosted` runner pool — switch
   `runs-on` to `ubuntu-latest` (the workflow comments call this out).
2. **SDK jar sourcing**: the upstream workflow fetches the Broadcom
   jar from a private repo via an `SDK_RUNTIME_SSH_KEY` deploy-key
   secret you won't have. Replace that step with your own source —
   e.g. store the appliance-extracted jar in your own private repo or
   an Actions secret/artifact store. Then **also update the
   `--sdk-jar` argument** on the `build-sdk` line of the workflow to
   point at wherever your replacement step puts the jar. The explicit
   `--sdk-jar` flag overrides `VCFCF_SDK_JAR`, so setting the env var
   alone is not enough — if you leave `--sdk-jar _sdk_runtime/...` in
   place the build will look for the upstream path and fail. Do **not**
   commit the jar to a public repo (no redistribution).

## Attribution

Native Java SDK rewrite of `vmbro/VCF-Operations-vCommunity` by Onur Yuzseven
(CC-licensed). Some original collectors (`host_install_date`,
`vm_scsi_controller_type`) carry dual attribution to Onur Yuzseven and Scott
Bowe; that dual attribution is preserved.
