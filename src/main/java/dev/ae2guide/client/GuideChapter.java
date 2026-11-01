package dev.ae2guide.client;

import net.minecraft.network.chat.Component;

public enum GuideChapter {
    START("start", "Getting started", "Materials, crystals and processors", "ae2:certus_quartz_crystal"),
    NETWORK("network", "ME networks", "Power, channels and wiring", "ae2:controller"),
    STORAGE("storage", "Storage", "Cells, filtering and external inventories", "ae2:drive"),
    AUTOMATION("automation", "Automation", "Transport and request-based crafting", "ae2:pattern_provider"),
    ADVANCED("advanced", "Advanced systems", "Subnetworks, P2P, quantum and spatial storage", "ae2:quantum_ring"),
    TROUBLESHOOTING("troubleshooting", "Troubleshooting", "Diagnose power, channel, storage and crafting problems", "ae2:network_tool"),
    OMNI_CELLS("ae2omnicells", "AE2 OMNI Cells", "Mixed storage, portable cells, filters and crafting", null),
    BIGGER_AE2("bigger_ae2", "Bigger AE2", "Quantum cells, singularity storage and crafting accelerators", null),
    AE2_THINGS("ae2things", "AE2 Things", "Disk construction, partitioning and reuse", null),
    AE2_MEGA_THINGS("ae2_mega_things", "AE2 MEGA Things", "MEGA item and fluid disks", null);

    private final String id;
    private final String en;
    private final String description;
    private final String icon;
    GuideChapter(String id, String en, String description, String icon) {
        this.id = id; this.en = en; this.description = description; this.icon = icon;
    }
    public String id() { return id; }
    public String en() { return en; }
    public String description() { return description; }
    public String icon() { return icon; }
    public boolean isAddon() { return icon == null; }
    public Component label() { return Component.translatable("ae2ponder.chapter." + id); }

    public static java.util.List<GuideChapter> active(java.util.List<GuideBook.Lesson> lessons) {
        var available = lessons.stream().map(GuideBook.Lesson::chapter).collect(java.util.stream.Collectors.toSet());
        return java.util.Arrays.stream(values()).filter(chapter -> !chapter.isAddon() || available.contains(chapter.id())).toList();
    }
}
