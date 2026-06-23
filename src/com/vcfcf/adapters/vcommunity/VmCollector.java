package com.vcfcf.adapters.vcommunity;

import com.integrien.alive.common.adapter3.Logger;
import com.vcfcf.adapters.vcommunity.VCommunityVSphereClient.MoInfo;
import com.vcfcf.adapters.vcommunity.VCommunityVSphereClient.MoRef;
import com.vcfcf.adapters.vcommunity.VCommunityVSphereClient.ScsiController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * VirtualMachine collector (vcommunity-vsphere pak). Reads the vSphere-side VM
 * surface over the vim25 vCenter session: snapshot count, VM options, advanced
 * parameters (extraConfig filtered to the central check-list), SCSI controllers,
 * and the passive VMware-Tools {@code Guest OS|Operating System|*} path
 * ({@code guest.detailedData}).
 *
 * <p><b>Surface scope (vcommunity-vsphere).</b> This pak owns ONLY the
 * vim25-sourced VM surface. The in-guest guest-ops surface (Windows services,
 * in-guest CSV OS-info, Windows event logs read through the vCenter
 * GuestOperationsManager) belongs to the {@code vcommunity-os} pak and was
 * stripped from this fork along with {@code GuestOpsClient}. The passive
 * {@code Guest OS|Operating System|*} keys KEPT here come from the VMware-Tools
 * {@code guest.detailedData} path (no Windows credential, no in-guest script);
 * the os pak's same-named keys come from the richer in-guest CSV path. While
 * the os pak is shelved, this pak is the SOLE writer of the six passive OS keys
 * (design 2026-06-23 OPEN-A refinement).
 */
final class VmCollector {

    /** Stitch tally for the world anchor. */
    static final class Result {
        int stitched;
    }

    static Result collect(VCommunityVSphereClient vs, VCommunityStitcher stitcher,
            Logger log, VCommunityConfig cfg, List<String> vmAdvParameters,
            boolean advUsable, List<String> vmOptions, boolean optionsUsable,
            long ts) throws Exception {
        Result result = new Result();
        List<MoInfo> vms = vs.getVms();

        for (MoInfo v : vms) {
            try {
                VCommunityStitcher.Entry e = stitcher.matchVm(v.name, v.moid);
                if (e == null) continue;

                Map<String, String> props = new LinkedHashMap<>();
                Map<String, Double> stats = new LinkedHashMap<>();

                collectConfig(vs, v, props, stats, vmAdvParameters, advUsable,
                        vmOptions, optionsUsable);

                stitcher.pushProperties(e.resourceId, props, ts);
                stitcher.pushStats(e.resourceId, stats, ts);
                result.stitched++;
            } catch (Exception ex) {
                log.warn("VmCollector: '" + v.name + "' failed (isolated): "
                        + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
        }
        log.info("VmCollector: stitched " + result.stitched + "/" + vms.size()
                + " VM(s)");
        return result;
    }

    // ---- pure vim25 config reads ----

    private static void collectConfig(VCommunityVSphereClient vs, MoInfo v,
            Map<String, String> props, Map<String, Double> stats,
            List<String> vmAdvParameters, boolean advUsable,
            List<String> vmOptions, boolean optionsUsable) throws Exception {
        MoRef vm = v.moRef;

        // Snapshot count (0 when none — a real reading).
        Integer snapCount = vs.vmSnapshotCount(vm);
        if (snapCount != null) {
            stats.put("vCommunity|Snapshot|Count", (double) snapCount);
        }

        // VM Options — per-key dotted config-path walk over the check-list.
        if (optionsUsable && !vmOptions.isEmpty()) {
            for (String configPath : vmOptions) {
                String value = vs.vmConfigPath(vm, configPath);
                if (value != null) {
                    props.put("vCommunity|Options|" + configPath, value);
                }
            }
        }

        // Advanced Parameters — extraConfig filtered to the check-list.
        if (advUsable && !vmAdvParameters.isEmpty()) {
            Map<String, String> extra = vs.vmExtraConfig(vm);
            for (String key : vmAdvParameters) {
                String value = extra.get(key);
                if (value != null) {
                    props.put("vCommunity|Configuration|Advanced Parameters|"
                            + key, value);
                }
            }
        }

        // SCSI controllers.
        List<ScsiController> ctrls = vs.vmScsiControllers(vm);
        stats.put("vCommunity|Configuration|SCSI Controllers|Count",
                (double) ctrls.size());
        for (ScsiController c : ctrls) {
            // Colon-instanced `Configuration|SCSI Controllers:{bus}|Type` is the
            // ONLY form current upstream emits. The legacy pipe form
            // `Config|SCSI Controllers|{bus}|Type` was RETIRED upstream in commit
            // d4633a6 (2025-11-20, "Virtual Machine SCSI Controller bug, fixes
            // #39"): that commit migrated the pipe key to this colon-instanced
            // key, flipped Count from with_property to with_metric, and commented
            // out the no-controller sentinel. The pipe key survives on prod only
            // as a frozen ghost property from pre-Nov-2025 collection; the live
            // source emits it nowhere. Re-emitting it would resurrect a key the
            // upstream author deliberately deleted — do not add it back.
            props.put("vCommunity|Configuration|SCSI Controllers:" + c.busNumber
                    + "|Type", c.friendlyType);
        }

        // Guest OS / Operating System — VMware-Tools guest info (vim25 guest.*),
        // the PASSIVE Tools path, NOT the Windows-only in-guest PowerShell path
        // (which belongs to the vcommunity-os pak). Populates for every VM whose
        // tools report it (including non-Windows guests), matching the prod
        // original verbatim. Each key is pushed only when the guest actually
        // reported it; an unreported field is SKIPPED, never a sentinel (the
        // cardinal unreadable-is-not-a-value rule). While the os pak is shelved,
        // this is the SOLE writer of these six keys (design OPEN-A refinement).
        Map<String, String> osInfo = vs.vmGuestOsInfo(vm);
        for (Map.Entry<String, String> e : osInfo.entrySet()) {
            props.put("vCommunity|Guest OS|Operating System|" + e.getKey(),
                    e.getValue());
        }
    }
}
