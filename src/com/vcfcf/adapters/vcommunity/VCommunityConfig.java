package com.vcfcf.adapters.vcommunity;

/**
 * Typed configuration POJO for the vCommunity vSphere adapter — a native Java
 * SDK rewrite of {@code vmbro/VCF-Operations-vCommunity} (Onur Yuzseven,
 * CC-licensed). This is the vSphere/vCenter fork ({@code vcfcf_vcommunity_vsphere})
 * of the unified adapter; the Windows guest-ops surface (Windows credential, the
 * Windows Monitoring enums, the two Windows config files) belongs to the
 * {@code vcommunity-os} pak and was stripped here.
 *
 * <p>Populated from the adapter-instance {@code ResourceConfig} in
 * {@link VCommunityAdapter#configureAdapter}. Immutable: every field is
 * {@code final} and set once.
 *
 * <p><b>Credential redaction.</b> {@link #password} is a secret. It is read here
 * and consumed only by the vim25 SOAP login ({@link VCommunityVSphereClient}). It
 * MUST NOT appear in any log line, exception message, or URL. Search this repo
 * for {@code // REDACT-SECRET}.
 *
 * <p>The four {@code *_config_file} fields hold the NAMES (no path, no
 * {@code .xml}) of vSphere-side check-list files in the VCF Ops central
 * configuration-file store under {@code SolutionConfig/}. Defaults match the
 * bundled file base names — see {@link SolutionConfigStore} and the design doc
 * Config section.
 */
public final class VCommunityConfig {

    public final String vcenterHost;
    public final int port;
    public final String username;
    public final String password;          // REDACT-SECRET
    public final boolean allowInsecure;

    // Central config-store file NAMES (no path / no extension). vSphere-side only.
    public final String esxiAdvSettingsConfigFile;
    public final String esxiVibDriverConfigFile;
    public final String vmAdvSettingsConfigFile;
    public final String vmConfigurationConfigFile;

    public VCommunityConfig(
            String vcenterHost, String port, String username, String password,
            String allowInsecure,
            String esxiAdvSettingsConfigFile, String esxiVibDriverConfigFile,
            String vmAdvSettingsConfigFile, String vmConfigurationConfigFile) {
        this.vcenterHost = nonBlank(vcenterHost, "localhost");
        this.port = parsePort(port);
        this.username = username != null ? username : "";
        this.password = password != null ? password : "";   // REDACT-SECRET
        // Strict-by-default: only the literal "true" opts into trust-all.
        this.allowInsecure = "true".equalsIgnoreCase(allowInsecure);

        this.esxiAdvSettingsConfigFile =
                nonBlank(esxiAdvSettingsConfigFile, "esxi_advanced_system_settings");
        this.esxiVibDriverConfigFile =
                nonBlank(esxiVibDriverConfigFile, "esxi_packages");
        this.vmAdvSettingsConfigFile =
                nonBlank(vmAdvSettingsConfigFile, "vm_advanced_parameters");
        this.vmConfigurationConfigFile =
                nonBlank(vmConfigurationConfigFile, "vm_options");
    }

    /** vCenter /sdk endpoint host (port carried separately for the URL). */
    public String soapHostPort() {
        return port == 443 ? vcenterHost : vcenterHost + ":" + port;
    }

    private static String nonBlank(String s, String dflt) {
        return (s != null && !s.trim().isEmpty()) ? s.trim() : dflt;
    }

    private static int parsePort(String p) {
        if (p == null || p.trim().isEmpty()) return 443;
        try {
            int v = Integer.parseInt(p.trim());
            return (v > 0 && v <= 65535) ? v : 443;
        } catch (NumberFormatException e) {
            return 443;
        }
    }
}
