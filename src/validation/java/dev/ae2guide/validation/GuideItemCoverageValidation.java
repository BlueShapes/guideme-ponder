package dev.ae2guide.validation;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import dev.ae2guide.GuideResources;
import dev.ae2guide.Platform;
import dev.ae2guide.client.CaptionTone;
import dev.ae2guide.client.CaptionPages;
import dev.ae2guide.client.GuideBook;
import dev.ae2guide.client.GuideCaptionTones;
import dev.ae2guide.client.GuideItemDescriptions;
import dev.ae2guide.client.GuideNavigation;
import dev.ae2guide.client.GuidePlugin;
import dev.ae2guide.client.GuideScenes;
import dev.ae2guide.client.GuideTextLayout;
import dev.ae2guide.mixin.PonderUIAccessor;
import guideme.Guides;
import guideme.internal.GuideMEClient;
import guideme.internal.search.GuideSearch;
import net.createmod.catnip.gui.NavigatableSimiScreen;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderTag;
import net.createmod.ponder.foundation.PonderTooltipHandler;
import net.createmod.ponder.foundation.ui.PonderButton;
import net.createmod.ponder.foundation.ui.PonderTagScreen;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;

import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;

/** Independent, opt-in item coverage audit driven by a registry fixture outside the guide definitions. */
final class GuideItemCoverageValidation {
    private static final String FIXTURE = "/assets/ae2ponder/validation/item_coverage.json";
    private static final String PROGRESS_FILE = "coverage-progress.json";
    private static final boolean STAFF_ONLY = Boolean.getBoolean("ae2ponder.validateStaffOnly");
    private static final boolean TEXT_ONLY = Boolean.getBoolean("ae2ponder.validateTextOnly");
    private static final List<String> ADDITIONAL_ITEM_IDS = readAdditionalItemIds();
    private static final List<String> TEXT_KEYS = readTextKeys();
    private static final String TEXT_SELECTION = System.getProperty("ae2ponder.validationTextSelection", "").trim();
    private static final long NATIVE_STAGE_TIMEOUT_NANOS = 60L * 1_000_000_000L;
    private static final long STAFF_REPLAY_TIMEOUT_NANOS = 120L * 1_000_000_000L;
    private static final long LOCALE_STAGE_TIMEOUT_NANOS = 180L * 1_000_000_000L;
    private static final long PROGRESS_HEARTBEAT_NANOS = 5L * 1_000_000_000L;
    private static final int MAX_PROGRESS_EVENTS = 64;
    private static final ResourceLocation NETWORK_TAG = GuideResources.of("ae2ponder", "network");
    private static final String STAFF_ID = "ae2:charged_staff";
    private static final ResourceLocation GUIDE_ID = GuideResources.parse("ae2:guide");
    private static final ResourceLocation GUIDE_SEARCH_PAGE = GuideResources.parse("ae2ponder:ponder.md");
    private static final List<String> RUNTIME_NAME_ITEM_IDS = List.of(
            "ae2:mysterious_cube", "ae2:certus_quartz_crystal",
            "ae2omnicells:omni_link_print_press", "ae2omnicells:complex_link_print_press",
            "ae2omnicells:multidimensional_expansion_print_press");
    private static final Set<String> FORGE_DESCRIPTIONS_WITHOUT_ITEMS = Set.of(
            "megacells_sky_bronze_materials", "megacells_portable_cell_workbench");
    private static final Map<String, String> DESCRIPTION_SCREENSHOT_ITEMS = Map.of(
            "ja_jp", "ae2:conversion_monitor", "ar_sa", "ae2:tiny_tnt", "hi_in", "ae2:conversion_monitor");

    private static boolean started;
    private static boolean finished;
    private static boolean passed;
    private static String failure;
    private static String platform;
    private static Fixture fixture;
    private static PlatformFixture selected;
    private static LocaleTextSelection textSelection;
    private static String textSelectionResourcePath;
    private static Screen returnScreen;
    private static String originalLocale;
    private static String originalOptionLanguage;
    private static int originalGuiScale;
    private static CompletableFuture<Void> reload;
    private static List<String> locales = List.of();
    private static int localeIndex;
    private static String reloadTarget;
    private static String pendingDescriptionScreenshot;
    private static String phase = "not_started";
    private static final List<WTarget> wTargets = new ArrayList<>();
    private static int targetIndex;
    private static WTarget currentTarget;
    private static int holdTicks;
    private static String navigationStage;
    private static int staffReplayStartTime = -1;
    private static int staffParticleReadyTime = -1;
    private static String watchdogStageKey;
    private static long watchdogStageStartedNanos;
    private static String observedProgressKey;
    private static long lastProgressWriteNanos;
    private static String lastLoggedPhase;
    private static int lastSummaryTarget = -1;
    private static String lastLoggedSpecialKey;
    private static Map<String, Object> lastProgressDiagnostic = Map.of();
    private static Map<String, Object> failureDiagnostic = Map.of();
    private static final ArrayDeque<Map<String, Object>> progressEvents = new ArrayDeque<>();
    private static float staffHealth;
    private static int factoryChecks;
    private static int realHoldChecks;
    private static int representativeCategoryClicks;
    private static int chargedStaffCategoryRoundTrips;
    private static boolean chargedStaffDataEquivalenceChecked;
    private static int standardBackChecks;
    private static int compiledExpectedLessons;
    private static int coveredItems;
    private static int describedItems;
    private static int ponderItems;
    private static int excludedItems;
    private static int changedCaptionUnits;
    private static int expandedDescriptionChecks;
    private static int expectedDescriptionScreenshotCount;
    private static int localizedKeysChecked;
    private static int localizedSearchLocalesChecked;
    private static int localizedSearchTermsChecked;
    private static int localizedIndexedCaptionsChecked;
    private static int expectedSelectedCaptionSearchTerms;
    private static final List<Map<String, Object>> itemAudit = new ArrayList<>();
    private static final List<Map<String, Object>> excludedItemAudit = new ArrayList<>();
    private static final Map<String, Object> cableAudit = new LinkedHashMap<>();
    private static final List<Map<String, Object>> entranceAudit = new ArrayList<>();
    private static final List<Map<String, Object>> localeAudit = new ArrayList<>();
    private static final List<Map<String, Object>> descriptionScreenshotAudit = new ArrayList<>();
    private static final List<String> staffSceneScreenshots = new ArrayList<>();
    private static final List<Map<String, Object>> staffParticleAudit = new ArrayList<>();
    private static final List<Map<String, Object>> overflows = new ArrayList<>();
    private static final Map<String, String> inputHashes = new LinkedHashMap<>();
    private static Map<String, Map<String, List<String>>> captionRoles = Map.of();

    private GuideItemCoverageValidation() {}

    /** Called once per client tick by GuideValidation after a world and player are ready. */
    static boolean tick() {
        if (finished) return true;
        Minecraft client = Minecraft.getInstance();
        try {
            if (!started) start(client);
            observeProgress(client);
            if (reload != null) {
                tickReload(client);
                return finished;
            }
            if (phase.equals("native_w")) {
                tickNativeW(client);
                return finished;
            }
            if (phase.equals("locales")) {
                tickLocales(client);
                return finished;
            }
            throw new IllegalStateException("Unexpected item coverage phase: " + phase);
        } catch (Throwable exception) {
            fail(client, exception);
            return finished;
        }
    }

    /** Partial or final machine-readable audit, suitable for the validation result JSON. */
    static Map<String, Object> result() {
        var result = new LinkedHashMap<String, Object>();
        result.put("mode", TEXT_ONLY ? "text_only" : STAFF_ONLY
                ? !TEXT_KEYS.isEmpty() ? "scoped_item_coverage"
                    : ADDITIONAL_ITEM_IDS.isEmpty() ? "staff_scene" : "staff_and_recovered_entries"
                : "item_coverage");
        result.put("passed", passed && finished);
        result.put("complete", finished);
        result.put("phase", phase);
        result.put("platform", platform);
        result.put("sourceHash", fixture == null ? null : fixture.sourceHash());
        result.put("fixtureResource", FIXTURE);
        result.put("inputHashes", Map.copyOf(inputHashes));
        result.put("fixtureCounts", selected == null ? Map.of() : Map.of(
                "playerItems", size(selected.playerItems()),
                "addonItems", size(selected.addonItems()),
                "excludedItems", size(selected.excludedItems()),
                "cableFamilies", size(selected.cableFamilies()),
                "expectedNewEntrances", size(selected.expectedNewEntrances())));
        result.put("coverageCounts", TEXT_ONLY ? Map.of("status", "not_run") : Map.ofEntries(
                Map.entry("functionalItems", coveredItems),
                Map.entry("itemsWithPonder", ponderItems),
                Map.entry("itemsWithShiftDescription", describedItems),
                Map.entry("excludedItems", excludedItems),
                Map.entry("compiledExpectedLessons", compiledExpectedLessons),
                Map.entry("factoryChecks", factoryChecks),
                Map.entry("nativeWLongHolds", realHoldChecks),
                Map.entry("representativeCategoryClicks", representativeCategoryClicks),
                Map.entry("chargedStaffCategoryRoundTrips", chargedStaffCategoryRoundTrips),
                Map.entry("chargedStaffDataEquivalenceChecked", chargedStaffDataEquivalenceChecked),
                Map.entry("standardBackChecks", standardBackChecks)));
        result.put("cableFamilies", TEXT_ONLY ? Map.of("status", "not_run") : Map.copyOf(cableAudit));
        result.put("itemEntrances", TEXT_ONLY ? Map.of("status", "not_run") : List.copyOf(itemAudit));
        result.put("excludedItemAudit", TEXT_ONLY ? Map.of("status", "not_run") : List.copyOf(excludedItemAudit));
        result.put("expectedNewEntrances", TEXT_ONLY ? Map.of("status", "not_run") : List.copyOf(entranceAudit));
        result.put("selectedAdditionalItems", ADDITIONAL_ITEM_IDS);
        result.put("selectedTextKeys", textSelection == null ? TEXT_KEYS : allSelectedTextKeys());
        result.put("selectedLocaleTextKeys", selectedLocaleTextKeys());
        result.put("localizedContent", localizedContentResult());
        result.put("localeAudit", List.copyOf(localeAudit));
        result.put("descriptionScreenshots", List.copyOf(descriptionScreenshotAudit));
        result.put("staffSceneScreenshots", TEXT_ONLY ? Map.of("status", "not_run") : List.copyOf(staffSceneScreenshots));
        result.put("staffParticleAudit", TEXT_ONLY ? Map.of("status", "not_run") : List.copyOf(staffParticleAudit));
        result.put("notRunScopes", staffOnlyNotRunScopes());
        result.put("scopeStatus", TEXT_ONLY ? Map.ofEntries(
                Map.entry("registryAudit", "not_run"),
                Map.entry("expectedEntranceAudit", "not_run"),
                Map.entry("optionalLessonCombinationFilter", "checked_by_validation_bootstrap"),
                Map.entry("cableFactoryAudit", "not_run"),
                Map.entry("nativeWNavigation", "not_run"),
                Map.entry("chargedStaffSceneAndStackAudit", "not_run"),
                Map.entry("localizedText", localeAudit.size() == expectedLocaleCount() ? "checked" : "pending"),
                Map.entry("localizedCaptionSearchOutsideTextKeys", "not_run"),
                Map.entry("selectedDescriptionScreenshots", expectedDescriptionScreenshotCount == 0 ? "not_required"
                        : descriptionScreenshotAudit.size() == expectedDescriptionScreenshotCount ? "checked" : "pending"))
                : Map.of());
        result.put("staffSceneScope", TEXT_ONLY ? Map.of("status", "not_run") : STAFF_ONLY ? Map.of(
                "nativeWTarget", "ae2:charged_staff at 0, 300, and 1000 AE plus selected additional Ponder entries",
                "nativeWTargetCount", wTargets.size(),
                "selectedAdditionalItems", ADDITIONAL_ITEM_IDS,
                "localeChecks", localeChecksEnabled() ? "22 locales, selected TextKeys only" : "not_run",
                "nativeCategoryAndBack", "checked for all three staff stacks",
                "completeStackRetention", "checked at W open and after category return",
                "independentRegistryAndExpectedEntrances", "checked",
                "cableFactoryCourses", "checked for all 85 fixture cable variants",
                "staffSceneScreenshotsRequired", 5,
                "staffSceneScreenshotsCaptured", staffSceneScreenshots.size()) : Map.of());
        result.put("progress", progressDetails(Minecraft.getInstance(), System.nanoTime()));
        if (!failureDiagnostic.isEmpty()) result.put("failureDiagnostic", failureDiagnostic);
        if (failure != null) result.put("failure", failure);
        return result;
    }

    private static Map<String, Object> localizedContentResult() {
        int expectedLocales = expectedLocaleCount();
        List<String> captionKeys = allSelectedTextKeys().stream()
                .filter(GuideCaptionValidation::isCaptionKey).toList();
        var localized = new LinkedHashMap<String, Object>();
        localized.put("status", !localeChecksEnabled() ? "not_run"
                : localeAudit.size() == expectedLocales ? "checked" : "pending");
        localized.put("locales", localeAudit.size());
        localized.put("expectedLocales", expectedLocales);
        localized.put("expectedTotalKeyChecks", expectedTextKeyCheckCount());
        localized.put("expectedIndexedCaptions", expectedIndexedCaptionCount());
        localized.put("perLocaleCounts", localeCounts());
        if (textSelection == null)
            localized.put("changedKeysPerLocale", localeChecksEnabled() ? effectiveLocaleKeys().size() : 0);
        localized.put("captionRoleLocalesHashed", captionRoles.size());
        localized.put("fixtureChangedKeys", fixture == null || fixture.changedKeys() == null ? 0 : fixture.changedKeys().size());
        localized.put("totalLocaleKeyChecks", localizedKeysChecked);
        localized.put("changedCaptionUnits", changedCaptionUnits);
        localized.put("expandedDescriptionChecks", expandedDescriptionChecks);
        localized.put("expectedExpandedDescriptionChecks", expectedDescriptionExpansionCount());
        localized.put("localizedSearch", TEXT_ONLY ? Map.of(
                "status", captionKeys.isEmpty() ? "not_required"
                        : localizedSearchLocalesChecked == expectedCaptionLocaleCount() ? "checked" : "pending",
                "locales", localizedSearchLocalesChecked,
                "expectedLocales", expectedCaptionLocaleCount(),
                "captionTerms", localizedSearchTermsChecked,
                "expectedCaptionTerms", textSelection == null ? localizedSearchTermsChecked
                        : expectedSelectedCaptionSearchTerms,
                "completeIndexedCaptions", localizedIndexedCaptionsChecked,
                "captionKeys", captionKeys) : Map.of("status", "not_run"));
        localized.put("expectedDescriptionScreenshots", expectedDescriptionScreenshotCount);
        localized.put("overflows", List.copyOf(overflows));
        return Map.copyOf(localized);
    }

    private static Map<String, Object> localeCounts() {
        var counts = new LinkedHashMap<String, Object>();
        for (Map<String, Object> locale : localeAudit) {
            var perLocale = new LinkedHashMap<String, Object>();
            perLocale.put("changedKeys", locale.getOrDefault("changedKeys", 0));
            perLocale.put("captionUnits", locale.getOrDefault("captionUnits", 0));
            perLocale.put("expandedDescriptions", locale.getOrDefault("expandedDescriptions", 0));
            perLocale.put("captionSearchTerms", locale.getOrDefault("localizedCaptionSearchTerms", 0));
            perLocale.put("completeIndexedCaptions", locale.getOrDefault("completeIndexedCaptions", 0));
            counts.put((String) locale.get("locale"), Map.copyOf(perLocale));
        }
        return Map.copyOf(counts);
    }

    private static List<String> staffOnlyNotRunScopes() {
        if (TEXT_ONLY && textSelection != null) return List.of(
                "functional registry presence, Ponder/Shift coverage, and excluded-item audits",
                "expected entrance registry/Ponder target matching and lesson compilation",
                "all 85 cable-family tag, course, and factory checks",
                "native W holds, category/back navigation, and standard Ponder UI checks",
                "charged staff stack equivalence, scene effects, replay, particles, and screenshots",
                "localized text, font, and expanded-description checks outside selected locale/key pairs",
                "GuideME searches for localized captions outside selected locale/key pairs");
        if (TEXT_ONLY) return List.of(
                "functional registry presence, Ponder/Shift coverage, and excluded-item audits",
                "expected entrance registry/Ponder target matching and lesson compilation",
                "all 85 cable-family tag, course, and factory checks",
                "native W holds, category/back navigation, and standard Ponder UI checks",
                "charged staff stack equivalence, scene effects, replay, particles, and screenshots",
                "22-locale text, font, and expanded-description checks for fixture keys outside TextKeys",
                "GuideME searches for localized captions outside selected TextKeys");
        if (!STAFF_ONLY) return List.of();
        String unselectedItems = ADDITIONAL_ITEM_IDS.isEmpty()
                ? "native W long-holds for non-staff items"
                : "native W long-holds for fixture items outside the selected additional targets (including cable variants)";
        if (TEXT_KEYS.isEmpty()) return List.of(unselectedItems,
                "22-locale translation reload, caption/font layout, and expanded-description rendering",
                "localized item-description screenshots");
        return List.of(unselectedItems,
                "localized source strings, caption/font layout, and expanded-description rendering outside selected TextKeys",
                "localized item-description screenshots outside selected TextKeys");
    }

    private static void start(Minecraft client) throws Exception {
        started = true;
        phase = TEXT_ONLY ? "text_only_setup" : "independent_registry_audit";
        if (TEXT_ONLY && STAFF_ONLY)
            throw new IllegalStateException("TextOnly and StaffOnly validation modes are mutually exclusive");
        if (TEXT_ONLY && TEXT_KEYS.isEmpty() && TEXT_SELECTION.isEmpty())
            throw new IllegalStateException("TextOnly requires TextKeys or a TextSelection resource");
        if (TEXT_ONLY && !TEXT_KEYS.isEmpty() && !TEXT_SELECTION.isEmpty())
            throw new IllegalStateException("TextOnly cannot combine TextKeys and TextSelection");
        if (!TEXT_ONLY && !TEXT_SELECTION.isEmpty())
            throw new IllegalStateException("TextSelection requires TextOnly mode");
        if (TEXT_ONLY && !ADDITIONAL_ITEM_IDS.isEmpty())
            throw new IllegalStateException("TextOnly cannot include AdditionalItems");
        if (!ADDITIONAL_ITEM_IDS.isEmpty() && !STAFF_ONLY)
            throw new IllegalStateException("Additional item W targets require StaffOnly mode");
        if (!TEXT_KEYS.isEmpty() && !STAFF_ONLY && !TEXT_ONLY)
            throw new IllegalStateException("TextKeys requires StaffOnly or TextOnly mode");
        for (String rawId : ADDITIONAL_ITEM_IDS) {
            exactId(rawId);
            if (rawId.equals(STAFF_ID))
                throw new IllegalStateException("Charged staff is already selected at three energy levels and cannot be an additional item target");
        }
        returnScreen = client.screen;
        originalLocale = client.getLanguageManager().getSelected();
        originalOptionLanguage = client.options.languageCode;
        originalGuiScale = client.options.guiScale().get();
        platform = Platform.isModLoaded("neoforge") ? "neoforge" : "forge";
        fixture = read(FIXTURE, Fixture.class);
        selected = fixture.platforms().get(platform);
        if (selected == null) throw new IllegalStateException("Coverage fixture has no " + platform + " section");
        if (fixture.sourceHash() == null || !fixture.sourceHash().matches("[0-9a-f]{64}"))
            throw new IllegalStateException("Coverage fixture has no valid reviewed SHA-256 source hash");
        if (fixture.changedKeys() == null || fixture.changedKeys().isEmpty()
                || fixture.changedKeys().stream().distinct().count() != fixture.changedKeys().size())
            throw new IllegalStateException("Coverage fixture changedKeys are empty or duplicated");
        List<String> supportedLocales = TEXT_ONLY ? readLanguageIds() : List.of();
        if (!TEXT_SELECTION.isEmpty()) {
            ResourceLocation selectionId = exactId(TEXT_SELECTION);
            if (!selectionId.getNamespace().equals("ae2ponder") || !selectionId.getPath().startsWith("validation/"))
                throw new IllegalStateException("TextSelection must identify an ae2ponder validation resource: " + TEXT_SELECTION);
            textSelectionResourcePath = "/assets/" + selectionId.getNamespace() + "/" + selectionId.getPath();
            textSelection = readTextSelection(textSelectionResourcePath);
            if (!platform.equals(textSelection.platform()))
                throw new IllegalStateException("TextSelection platform mismatch: expected=" + platform
                        + ", selection=" + textSelection.platform());
            if (textSelection.locales() == null || textSelection.locales().isEmpty())
                throw new IllegalStateException("TextSelection must declare at least one locale");
            for (var selection : textSelection.locales().entrySet()) {
                String locale = selection.getKey();
                List<String> keys = selection.getValue();
                if (!supportedLocales.contains(locale))
                    throw new IllegalStateException("TextSelection contains an unsupported locale: " + locale);
                if (keys == null || keys.isEmpty())
                    throw new IllegalStateException("TextSelection locale has no translation keys: " + locale);
                if (keys.stream().anyMatch(key -> key == null || !key.matches("[a-z0-9_.-]+")))
                    throw new IllegalStateException("TextSelection contains an invalid translation key for " + locale);
                if (keys.stream().distinct().count() != keys.size())
                    throw new IllegalStateException("TextSelection contains duplicate translation keys for " + locale);
            }
        }
        List<String> requestedTextKeys = allSelectedTextKeys();
        if (!fixture.changedKeys().containsAll(requestedTextKeys)) {
            Set<String> unknownKeys = new TreeSet<>(requestedTextKeys);
            unknownKeys.removeAll(fixture.changedKeys());
            throw new IllegalStateException("Selected localized keys must be changed keys in the independent fixture: " + unknownKeys);
        }
        if (textSelection != null) {
            for (var selection : textSelection.locales().entrySet()) {
                Map<String, String> own = readLanguage(selection.getKey());
                for (String key : selection.getValue()) {
                    String raw = own.get(key);
                    if (raw == null || raw.isBlank())
                        throw new IllegalStateException("TextSelection key is absent from " + selection.getKey() + ": " + key);
                    if (GuideCaptionValidation.isCaptionKey(key)) {
                        int units = dev.ae2guide.client.CaptionPages.split(raw).size();
                        if (units == 0)
                            throw new IllegalStateException("TextSelection caption has no authored search units: "
                                    + selection.getKey() + "/" + key);
                        expectedSelectedCaptionSearchTerms += units;
                    }
                }
            }
        }
        captureInputHashes();
        if (TEXT_ONLY) {
            locales = textSelection == null ? supportedLocales
                    : textSelection.locales().keySet().stream().sorted().toList();
            if (locales.isEmpty()) throw new IllegalStateException("TextOnly found no supported locales");
            localeIndex = 0;
            phase = "locales";
            beginLocaleReload(client, locales.get(localeIndex));
            return;
        }
        auditIndependentRegistry(client);
        auditExpectedEntrances();
        auditCableFamilies();
        createWTargets();
        if (STAFF_ONLY) {
            Set<String> requestedAdditionalItems = Set.copyOf(ADDITIONAL_ITEM_IDS);
            Set<String> foundAdditionalItems = new LinkedHashSet<>();
            List<WTarget> selectedTargets = new ArrayList<>();
            for (WTarget target : wTargets) {
                if (target.energy() != null) {
                    selectedTargets.add(target);
                } else if (requestedAdditionalItems.contains(target.itemId())) {
                    selectedTargets.add(target);
                    foundAdditionalItems.add(target.itemId());
                }
            }
            if (!foundAdditionalItems.equals(requestedAdditionalItems)) {
                Set<String> missing = new TreeSet<>(requestedAdditionalItems);
                missing.removeAll(foundAdditionalItems);
                throw new IllegalStateException("AdditionalItems did not resolve to exact registered Ponder W targets: " + missing);
            }
            wTargets.clear();
            wTargets.addAll(selectedTargets);
            if (wTargets.size() != 3 + ADDITIONAL_ITEM_IDS.size()
                    || wTargets.stream().filter(target -> target.energy() != null).map(WTarget::energy).distinct().count() != 3)
                throw new IllegalStateException("StaffOnly requires exactly the 0, 300, and 1000 AE native W targets");
        }
        if (wTargets.isEmpty()) throw new IllegalStateException("No native W targets were selected");
        locales = localeChecksEnabled() ? readLanguageIds() : List.of();
        client.options.guiScale().set(4);
        client.resizeDisplay();
        client.setScreen(new InventoryScreen(client.player));
        targetIndex = 0;
        currentTarget = wTargets.get(0);
        navigationStage = "hold";
        phase = "native_w";
    }

    private static void auditIndependentRegistry(Minecraft client) {
        requireUnique(selected.playerItems(), "playerItems");
        requireUnique(selected.addonItems(), "addonItems");
        requireUnique(selected.excludedItems(), "excludedItems");
        if (!selected.excludedItems().isEmpty() && selected.excludedReasons() == null)
            throw new IllegalStateException("Independent fixture must explain every excluded registry item");
        if (selected.excludedReasons() != null) {
            for (String id : selected.excludedItems())
                if (!selected.excludedReasons().containsKey(id) || selected.excludedReasons().get(id) == null)
                    throw new IllegalStateException("Independent fixture has no exclusion reason/source for " + id);
        }
        Set<String> player = Set.copyOf(selected.playerItems());
        Set<String> addons = Set.copyOf(selected.addonItems());
        Set<String> excluded = Set.copyOf(selected.excludedItems());
        if (!java.util.Collections.disjoint(player, addons) || !java.util.Collections.disjoint(player, excluded)
                || !java.util.Collections.disjoint(addons, excluded))
            throw new IllegalStateException("Independent item groups overlap");
        if (selected.cableFamilies().size() != 5)
            throw new IllegalStateException("Expected five independent cable families, found " + selected.cableFamilies().size());
        int cableCount = 0;
        Set<String> familyItems = new LinkedHashSet<>();
        for (var family : selected.cableFamilies().entrySet()) {
            requireUnique(family.getValue().items(), "cableFamilies." + family.getKey());
            if (family.getValue().items().size() != 17)
                throw new IllegalStateException("Cable family must contain all 17 color/neutral variants: " + family.getKey());
            if (!"network".equals(family.getValue().chapter()))
                throw new IllegalStateException("Cable family is assigned to a non-network chapter: " + family.getKey());
            for (String item : family.getValue().items())
                if (!familyItems.add(item))
                    throw new IllegalStateException("Cable item occurs in more than one family: " + item);
            cableCount += family.getValue().items().size();
        }
        if (cableCount != 85 || !player.containsAll(familyItems))
            throw new IllegalStateException("Independent cable expectation is not 85 core registry items");

        var optionalMods = selected.addonItems().stream().map(GuideItemCoverageValidation::namespace)
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        boolean addonsExpected = Boolean.getBoolean("ae2ponder.expectAddons");
        var loadedOptional = optionalMods.stream().filter(Platform::isModLoaded).collect(java.util.stream.Collectors.toSet());
        if (addonsExpected && !loadedOptional.equals(optionalMods))
            throw new IllegalStateException("All fixture add-on namespaces must be loaded: expected=" + optionalMods
                    + ", loaded=" + loadedOptional);
        if (!addonsExpected && !loadedOptional.isEmpty())
            throw new IllegalStateException("Add-on namespaces were expected to be absent: " + loadedOptional);
        if (!addonsExpected) {
            for (String rawId : selected.addonItems()) {
                ResourceLocation id = exactId(rawId);
                if (BuiltInRegistries.ITEM.containsKey(id))
                    throw new IllegalStateException("Optional add-on item leaked into the no-add-on registry: " + rawId);
            }
        }

        var functional = new ArrayList<String>(selected.playerItems());
        if (addonsExpected) functional.addAll(selected.addonItems());
        int actualPonder = 0;
        int actualDescriptions = 0;
        for (String rawId : functional) {
            ResourceLocation id = exactId(rawId);
            if (!Platform.isModLoaded(id.getNamespace()))
                throw new IllegalStateException("Expected item namespace is not loaded: " + rawId);
            var item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
            if (item == null) throw new IllegalStateException("Independent expected registry item is missing: " + rawId);
            ItemStack stack = new ItemStack(item);
            boolean hasPonder = PonderIndex.getSceneAccess().doScenesExistForId(id);
            GuideItemDescriptions.Entry description = GuideItemDescriptions.descriptionFor(stack);
            boolean hasDescription = description != null;
            if (!hasPonder && !hasDescription)
                throw new IllegalStateException("Functional registry item has neither Ponder nor Shift entry: " + rawId);
            if (hasPonder) actualPonder++;
            if (hasDescription) actualDescriptions++;
            itemAudit.add(Map.of("item", rawId, "ponder", hasPonder,
                    "description", hasDescription ? description.id() : ""));
        }
        for (String rawId : selected.excludedItems()) {
            ResourceLocation id = exactId(rawId);
            boolean namespaceLoaded = Platform.isModLoaded(id.getNamespace());
            boolean registered = BuiltInRegistries.ITEM.containsKey(id);
            Object reason = selected.excludedReasons() == null ? null : selected.excludedReasons().get(rawId);
            var audit = new LinkedHashMap<String, Object>();
            audit.put("item", rawId);
            audit.put("reason", reason == null ? "excluded by independent fixture" : reason);
            if (reason instanceof Map<?, ?> evidence) {
                audit.put("source", Objects.toString(evidence.get("source"), ""));
                audit.put("requiresMods", evidence.get("requiresMods") == null ? List.of() : evidence.get("requiresMods"));
            } else {
                audit.put("source", "");
                audit.put("requiresMods", List.of());
            }
            audit.put("namespaceLoaded", namespaceLoaded);
            audit.put("registered", registered);
            excludedItemAudit.add(Map.copyOf(audit));
        }
        coveredItems = functional.size();
        ponderItems = actualPonder;
        describedItems = actualDescriptions;
        excludedItems = selected.excludedItems().size();
        if (functional.size() != selected.playerItems().size() + (addonsExpected ? selected.addonItems().size() : 0))
            throw new IllegalStateException("Registry audit count disagrees with independent fixture");
        GuideDescriptionValidation.verifyHooks();
    }

    private static void auditExpectedEntrances() {
        var supportedMods = Platform.loadedModIds();
        Set<String> expectedItems = new LinkedHashSet<>(selected.playerItems());
        if (Boolean.getBoolean("ae2ponder.expectAddons")) expectedItems.addAll(selected.addonItems());
        Set<String> fixtureItems = new LinkedHashSet<>(selected.playerItems());
        fixtureItems.addAll(selected.addonItems());
        for (var itemExpected : selected.expectedNewEntrances().entrySet()) {
            String rawItemId = itemExpected.getKey();
            if (!fixtureItems.contains(rawItemId))
                throw new IllegalStateException("New entrance target is not in the independent fixture item set: " + rawItemId);
            ResourceLocation itemId = exactId(rawItemId);
            if (!supportedMods.contains(itemId.getNamespace())) {
                entranceAudit.add(Map.of("item", rawItemId, "ponder", availableExpectedLessonIds(itemExpected.getValue().ponder()),
                        "description", Objects.toString(itemExpected.getValue().description(), ""),
                        "availability", "optional_namespace_absent", "matched", true));
                continue;
            }
            if (!expectedItems.contains(rawItemId))
                throw new IllegalStateException("Loaded new entrance target is not in the runtime expected set: " + rawItemId);
            var item = BuiltInRegistries.ITEM.getOptional(itemId)
                    .orElseThrow(() -> new IllegalStateException("New entrance item is missing: " + rawItemId));
            var expected = itemExpected.getValue();
            List<String> expectedScenes = availableExpectedLessonIds(expected.ponder());
            var actualScenes = registeredGuideSceneIds(itemId);
            if (!actualScenes.equals(expectedScenes))
                throw new IllegalStateException("New Ponder targets differ from the independent fixture for " + rawItemId
                        + ": expected=" + expectedScenes + ", actual=" + actualScenes);
            GuideItemDescriptions.Entry actualEntry = GuideItemDescriptions.descriptionFor(new ItemStack(item));
            String actualDescription = actualEntry == null ? null : actualEntry.id();
            if (!Objects.equals(expected.description(), actualDescription))
                throw new IllegalStateException("New Shift target differs from the independent fixture for " + rawItemId
                        + ": expected=" + expected.description() + ", actual=" + actualDescription);
            for (String lessonId : expectedScenes) {
                var entry = GuidePlugin.entry(exactId(lessonId).getPath());
                var scenes = PonderIndex.getSceneAccess().compile(List.of(entry));
                String sceneId = lessonId;
                if (scenes.size() != 1 || !scenes.get(0).getId().toString().equals(sceneId))
                    throw new IllegalStateException("Expected lesson did not compile to its declared scene: " + sceneId);
                compiledExpectedLessons++;
            }
            if (expectedScenes.isEmpty() && (actualDescription == null || actualDescription.isBlank()))
                throw new IllegalStateException("New entrance target has neither expected scene nor description: " + rawItemId);
            entranceAudit.add(Map.of("item", rawItemId, "ponder", expectedScenes,
                    "description", expected.description() == null ? "" : expected.description(), "matched", true));
        }
    }

    private static void auditCableFamilies() throws Exception {
        for (var family : selected.cableFamilies().entrySet()) {
            String familyId = family.getKey();
            CableFamily definition = family.getValue();
            if (definition.firstLesson() == null || definition.firstLesson().isBlank())
                throw new IllegalStateException("Cable family has no expected first lesson: " + familyId);
            var expectedTag = GuideResources.of("ae2ponder", definition.chapter());
            List<String> course = null;
            List<String> fluixCourse = null;
            String fluixItem = null;
            Set<ResourceLocation> tags = null;
            int checked = 0;
            for (String rawId : definition.items()) {
                ResourceLocation itemId = exactId(rawId);
                var item = BuiltInRegistries.ITEM.getOptional(itemId)
                        .orElseThrow(() -> new IllegalStateException("Cable variant is not registered: " + rawId));
                if (!PonderIndex.getSceneAccess().doScenesExistForId(itemId))
                    throw new IllegalStateException("Cable variant has no actual Ponder registration: " + rawId);
                var actualTags = PonderIndex.getTagAccess().getTags(itemId).stream().map(PonderTag::getId)
                        .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
                if (!actualTags.contains(expectedTag))
                    throw new IllegalStateException("Cable variant is missing its expected network category: " + rawId
                            + ", tags=" + actualTags);
                if (!PonderIndex.getTagAccess().getItems(expectedTag).contains(itemId))
                    throw new IllegalStateException("Cable variant is absent from the standard network tag items: " + rawId);
                var ui = PonderUI.of(new ItemStack(item));
                List<String> ids = sceneIds(ui);
                if (ids.isEmpty() || !ids.get(0).equals(GuideResources.of("ae2ponder", definition.firstLesson()).toString()))
                    throw new IllegalStateException("Cable W factory did not select the expected first lesson: " + rawId
                            + ", expected=" + definition.firstLesson() + ", actual=" + ids);
                if (course == null) course = ids;
                else if (!course.equals(ids))
                    throw new IllegalStateException("Cable variants in one family compile different courses: " + familyId
                            + ", item=" + rawId + ", expected=" + course + ", actual=" + ids);
                if (tags == null) tags = actualTags;
                else if (!tags.equals(actualTags))
                    throw new IllegalStateException("Cable variants in one family have different category tags: " + familyId
                            + ", item=" + rawId + ", expected=" + tags + ", actual=" + actualTags);
                if (itemId.getPath().contains("fluix")) {
                    if (fluixItem != null)
                        throw new IllegalStateException("Cable family contains multiple fluix baselines: " + familyId);
                    fluixItem = rawId;
                    fluixCourse = ids;
                }
                factoryChecks++;
                checked++;
            }
            if (fluixItem == null || fluixCourse == null)
                throw new IllegalStateException("Cable family has no independently identified fluix variant: " + familyId);
            if (!course.equals(fluixCourse))
                throw new IllegalStateException("Cable family variants do not share the fluix variant's course: " + familyId);
            if (!NETWORK_TAG.equals(expectedTag) || tags == null || !tags.contains(NETWORK_TAG))
                throw new IllegalStateException("Cable family does not have the expected network category: " + familyId);
            cableAudit.put(familyId, Map.of("expectedItems", definition.items().size(), "factoryItems", checked,
                    "firstLesson", definition.firstLesson(), "chapter", definition.chapter(),
                    "sceneIds", course == null ? List.of() : course,
                    "tagIds", tags == null ? List.of() : tags.stream().map(ResourceLocation::toString).toList(),
                    "fluixItem", fluixItem, "fluixCourse", fluixCourse,
                    "matchesFluixVariant", true));
        }
    }

    private static void createWTargets() throws Exception {
        Set<String> included = new LinkedHashSet<>();
        for (var family : selected.cableFamilies().entrySet()) {
            for (int i = 0; i < family.getValue().items().size(); i++) {
                String itemId = family.getValue().items().get(i);
                var item = BuiltInRegistries.ITEM.getOptional(exactId(itemId)).orElseThrow();
                @SuppressWarnings("unchecked")
                List<String> familyCourse = (List<String>) ((Map<?, ?>) cableAudit.get(family.getKey())).get("sceneIds");
                wTargets.add(new WTarget(itemId, new ItemStack(item),
                        familyCourse, family.getKey(), family.getValue().chapter(), i == 0, null));
                included.add(itemId);
            }
        }
        for (var target : selected.expectedNewEntrances().entrySet()) {
            List<String> expectedScenes = availableExpectedLessonIds(target.getValue().ponder());
            if (expectedScenes.isEmpty() || included.contains(target.getKey())
                    || target.getKey().equals(STAFF_ID)
                    || !Platform.isModLoaded(namespace(target.getKey()))) continue;
            var item = BuiltInRegistries.ITEM.getOptional(exactId(target.getKey())).orElseThrow();
            wTargets.add(new WTarget(target.getKey(), new ItemStack(item), expectedScenes,
                    null, null, false, null));
            included.add(target.getKey());
        }
        wTargets.sort(Comparator.comparing((WTarget target) -> target.family() == null ? "z-" + target.itemId() : target.family())
                .thenComparing(target -> target.itemId()));
        // Keep the three energy-bearing variants together and make the 1000 AE scene replay the final W sample.
        for (double energy : List.of(0.0, 300.0, 1000.0))
            wTargets.add(new WTarget(STAFF_ID + "#" + energy, staffStack(energy),
                    List.of(GuideResources.of("ae2ponder", "charged_staff").toString()), null, "start", true, energy));
        verifyStaffDataEquivalence();
    }

    private static void verifyStaffDataEquivalence() throws Exception {
        ItemStack empty = staffStack(0.0);
        ItemStack charged = staffStack(300.0);
        PonderUI emptyUi = PonderUI.of(empty.copy());
        PonderUI sameUi = PonderUI.of(empty.copy());
        PonderUI chargedUi = PonderUI.of(charged.copy());
        if (!ItemStack.matches(empty, emptyUi.getSubject()) || !ItemStack.matches(charged, chargedUi.getSubject()))
            throw new IllegalStateException("Ponder factory did not preserve complete charged staff stack data");
        if (!emptyUi.isEquivalentTo(sameUi)
                || emptyUi.isEquivalentTo(chargedUi) || chargedUi.isEquivalentTo(emptyUi))
            throw new IllegalStateException("Ponder factory equivalence did not distinguish charged staff energy data");
        chargedStaffDataEquivalenceChecked = true;
    }

    private static void tickNativeW(Minecraft client) throws Exception {
        if (targetIndex >= wTargets.size()) {
            finishWAndStartLocales(client);
            return;
        }
        currentTarget = wTargets.get(targetIndex);
        if (client.getOverlay() != null) return;
        if (client.screen instanceof PonderTagScreen tagScreen) {
            if (navigationStage.equals("tag_screen")) {
                PonderTag tag = tagScreen.getTag();
                ResourceLocation expected = GuideResources.of("ae2ponder", currentTarget.familyChapter());
                if (!tag.getId().equals(expected))
                    throw new IllegalStateException("Native category click opened the wrong tag: expected=" + expected
                            + ", actual=" + tag.getId());
                if (PonderIndex.getTagAccess().getItems(tag).isEmpty())
                    throw new IllegalStateException("Native category route opened an empty item tag: " + expected);
                navigationStage = "tag_back";
                return;
            }
            if (navigationStage.equals("tag_back") && clickPonderBackIfReady(tagScreen))
                navigationStage = "category_return";
            return;
        }
        if (client.screen instanceof PonderUI ui) {
            if (navigationStage.equals("hold")) {
                releaseKey();
                verifyNativeW(ui);
                realHoldChecks++;
                holdTicks = 0;
                navigationStage = currentTarget.energy() != null && currentTarget.energy() == 1000.0
                        ? "staff_target" : currentTarget.categoryRepresentative() ? "category" : "ui_back";
                return;
            }
            if (navigationStage.startsWith("staff_")) {
                tickStaffScene(ui);
                return;
            }
            if (navigationStage.equals("category")) {
                PonderTag category = PonderIndex.getTagAccess().getRegisteredTag(
                        GuideResources.of("ae2ponder", currentTarget.familyChapter()));
                if (!category.getId().equals(GuideResources.of("ae2ponder", currentTarget.familyChapter())))
                    throw new IllegalStateException("Cable category tag is unregistered: " + currentTarget.familyChapter());
                if (!clickCategory(ui, category)) return;
                representativeCategoryClicks++;
                navigationStage = "tag_screen";
                return;
            }
            if (navigationStage.equals("tag_back")) {
                throw new IllegalStateException("Native Ponder tag back returned to the scene before being validated");
            }
            if (navigationStage.equals("category_return")) {
                if (!ui.getActiveScene().getId().toString().equals(currentTarget.expectedScenes().get(0)))
                    throw new IllegalStateException("Native tag back changed the selected cable lesson: " + currentTarget.itemId());
                verifyNativeW(ui);
                if (currentTarget.energy() != null) chargedStaffCategoryRoundTrips++;
                standardBackChecks++;
                navigationStage = "ui_back";
                return;
            }
            if (navigationStage.equals("ui_back")) {
                if (clickPonderBackIfReady(ui)) navigationStage = "inventory_return";
                return;
            }
            return;
        }
        if (navigationStage.equals("inventory_return") || navigationStage.equals("hold")) {
            if (!(client.screen instanceof InventoryScreen) && !(client.screen instanceof CreativeModeInventoryScreen)) {
                if (navigationStage.equals("inventory_return")) return;
                client.setScreen(new InventoryScreen(client.player));
                holdTicks = 0;
                return;
            }
            if (navigationStage.equals("inventory_return")) {
                if (client.getOverlay() != null) return;
                standardBackChecks++;
                advanceTarget(client);
                return;
            }
            holdNativeTooltip(client);
            return;
        }
        throw new IllegalStateException("Unexpected native W navigation stage " + navigationStage + " for " + currentTarget.itemId());
    }

    private static void holdNativeTooltip(Minecraft client) {
        if (!PonderTooltipHandler.enable)
            throw new IllegalStateException("Native Ponder W tooltip handler is disabled");
        ValidationPlatform.setPonderKeyDown(true);
        try {
            PonderTooltipHandler.tick();
            PonderTooltipHandler.addToTooltip(new ArrayList<>(), currentTarget.stack().copy());
            holdTicks++;
            if (client.screen instanceof PonderUI) return;
            if (holdTicks > 40)
                throw new IllegalStateException("Native W key hold did not open Ponder for " + currentTarget.itemId()
                        + " after " + holdTicks + " tooltip callback ticks");
        } finally {
            if (client.screen instanceof PonderUI) releaseKey();
        }
    }

    private static void verifyNativeW(PonderUI ui) throws Exception {
        String rawItem = currentTarget.itemId().split("#", 2)[0];
        ItemStack actualSubject = ui.getSubject();
        if (actualSubject.isEmpty()
                || !BuiltInRegistries.ITEM.getKey(actualSubject.getItem()).toString().equals(rawItem))
            throw new IllegalStateException("Standard W hold opened a different item: requested=" + rawItem
                    + ", subject=" + (actualSubject.isEmpty() ? "<empty>" : BuiltInRegistries.ITEM.getKey(actualSubject.getItem())));
        if (currentTarget.energy() != null) {
            double actualEnergy = staffEnergy(actualSubject);
            if (Math.abs(actualEnergy - currentTarget.energy()) > 0.0001)
                throw new IllegalStateException("Standard W changed charged staff data: requested=" + currentTarget.energy()
                        + ", actual=" + actualEnergy);
            if (!ItemStack.matches(currentTarget.stack(), actualSubject))
                throw new IllegalStateException("Ponder subject did not retain the complete charged staff stack: "
                        + currentTarget.energy());
            if (!ItemStack.matches(currentTarget.stack(), staffStack(currentTarget.energy())))
                throw new IllegalStateException("Standard W mutated the charged staff input stack: " + currentTarget.energy());
        } else if (!currentTarget.stack().isEmpty() && !ItemStack.matches(currentTarget.stack(), actualSubject)) {
            throw new IllegalStateException("Standard W changed ItemStack data while opening " + rawItem);
        }
        var actualScenes = sceneIds(ui);
        if (!actualScenes.equals(currentTarget.expectedScenes()))
            throw new IllegalStateException("Standard W opened the wrong Ponder course for " + rawItem
                    + ": expected=" + currentTarget.expectedScenes() + ", actual=" + actualScenes);
    }

    private static void tickStaffScene(PonderUI ui) throws Exception {
        GuideBook.Lesson lesson = GuideBook.lessons().stream().filter(value -> value.id().equals("charged_staff"))
                .findFirst().orElseThrow(() -> new IllegalStateException("Charged staff lesson is unavailable"));
        PonderScene scene = ui.getActiveScene();
        int targetStep = effectStep(lesson, "staff_target");
        int chargeStep = effectStep(lesson, "staff_charge");
        int chargedHitStep = effectStep(lesson, "staff_hit");
        int ordinaryHitStep = effectStep(lesson, "staff_empty_hit");
        switch (navigationStage) {
            case "staff_target" -> {
                seekEffect(ui, targetStep);
                navigationStage = "staff_target_check";
            }
            case "staff_target_check" -> {
                if (!awaitEffect(scene, targetStep, false)) return;
                Husk husk = requireStaffTarget(scene);
                staffHealth = husk.getHealth();
                if (staffHealth <= 0 || !husk.isAlive()) throw new IllegalStateException("Charged staff target is not a living entity");
                GuideValidation.screenshot("item-coverage-staff-target.png");
                staffSceneScreenshots.add("item-coverage-staff-target.png");
                navigationStage = "staff_charge";
            }
            case "staff_charge" -> {
                seekEffect(ui, chargeStep);
                navigationStage = "staff_charge_check";
            }
            case "staff_charge_check" -> {
                if (!awaitEffect(scene, chargeStep, true)) return;
                if (requireStaffTarget(scene).getHealth() != staffHealth)
                    throw new IllegalStateException("Charged staff charge effect changed the target health");
                GuideValidation.screenshot("item-coverage-staff-charge.png");
                requireVisibleStaffParticles(scene, "charge");
                staffSceneScreenshots.add("item-coverage-staff-charge.png");
                navigationStage = "staff_charged_hit";
            }
            case "staff_charged_hit" -> {
                seekEffect(ui, chargedHitStep);
                navigationStage = "staff_charged_hit_check";
            }
            case "staff_charged_hit_check" -> {
                if (!awaitEffect(scene, chargedHitStep, true)) return;
                Husk husk = requireStaffTarget(scene);
                if (husk.getHealth() != staffHealth || husk.hurtTime <= 0)
                    throw new IllegalStateException("Charged staff storyboard did not preserve health and show the ordinary red hurt flash");
                GuideValidation.screenshot("item-coverage-staff-electric-hit.png");
                requireVisibleStaffParticles(scene, "electric_hit");
                staffSceneScreenshots.add("item-coverage-staff-electric-hit.png");
                navigationStage = "staff_ordinary_hit";
            }
            case "staff_ordinary_hit" -> {
                seekEffect(ui, ordinaryHitStep);
                navigationStage = "staff_ordinary_hit_check";
            }
            case "staff_ordinary_hit_check" -> {
                if (!awaitEffect(scene, ordinaryHitStep, true)) return;
                Husk husk = requireStaffTarget(scene);
                if (husk.getHealth() != staffHealth || husk.hurtTime <= 0)
                    throw new IllegalStateException("Empty staff storyboard did not preserve health and show the ordinary red hurt flash");
                GuideValidation.screenshot("item-coverage-staff-ordinary-hit.png");
                requireVisibleStaffParticles(scene, "ordinary_hit");
                staffSceneScreenshots.add("item-coverage-staff-ordinary-hit.png");
                navigationStage = "staff_seek_end_for_replay";
            }
            case "staff_seek_end_for_replay" -> {
                int endTime = scene.getTotalTime() - 1;
                if (endTime < scene.getCurrentTime())
                    throw new IllegalStateException("Staff replay preparation would seek backward");
                ui.seekToTime(endTime);
                navigationStage = "staff_wait_replay";
            }
            case "staff_wait_replay" -> {
                if (!scene.isFinished()) return;
                var replay = ponderButton(ui, "replay");
                if (!replay.isVisible() || !replay.active) return;
                staffReplayStartTime = scene.getCurrentTime();
                clickWidget(ui, replay, "Native Ponder replay");
                navigationStage = "staff_replayed";
            }
            case "staff_replayed" -> {
                if (ui.getActiveScene() != scene) throw new IllegalStateException("Native replay changed the staff scene");
                if (staffReplayStartTime < 0 || scene.getCurrentTime() >= staffReplayStartTime) return;
                seekEffect(ui, chargedHitStep);
                staffReplayStartTime = -1;
                navigationStage = "staff_replay_check";
            }
            case "staff_replay_check" -> {
                if (!awaitEffect(scene, chargedHitStep, true)) return;
                Husk husk = requireStaffTarget(scene);
                if (husk.getHealth() != staffHealth || husk.hurtTime <= 0)
                    throw new IllegalStateException("Native replay did not restore the charged staff target and forward hit");
                GuideValidation.screenshot("item-coverage-staff-replay-hit.png");
                requireVisibleStaffParticles(scene, "replay_electric_hit");
                staffSceneScreenshots.add("item-coverage-staff-replay-hit.png");
                navigationStage = "category";
            }
            default -> throw new IllegalStateException("Unexpected charged staff stage: " + navigationStage);
        }
    }

    private static void seekEffect(PonderUI ui, int step) {
        staffParticleReadyTime = -1;
        // Stop two ticks before GuideScenes applies the effect so normal playback renders its particles.
        PonderScene scene = ui.getActiveScene();
        int target = scene.getKeyframeTime(step) + 12 - 2;
        int lastTick = scene.getTotalTime() - 1;
        if (target > lastTick)
            throw new IllegalStateException("Staff effect starts beyond the end of its Ponder scene: step=" + step);
        // The initial UI may already be past the step-zero pre-effect point. Let it advance naturally.
        if (target > scene.getCurrentTime()) ui.seekToTime(target);
    }

    private static boolean awaitEffect(PonderScene scene, int step, boolean needsParticles) throws Exception {
        // The blocking delay completes before emission, then particles enter the next world tick's
        // render bucket. Wait one more rendered frame before reading the main render target.
        int effectObservedAt = scene.getKeyframeTime(step) + 12 + 3;
        if (effectObservedAt >= scene.getTotalTime())
            throw new IllegalStateException("Staff effect observation point is beyond the end of its Ponder scene: step=" + step);
        int currentTime = scene.getCurrentTime();
        if (currentTime < effectObservedAt) return false;
        if (!needsParticles) return true;
        var buckets = (Map<?, ?>) requiredField(requiredField(scene.getWorld(), "particles"), "byType");
        int count = buckets.values().stream().mapToInt(value -> ((Collection<?>) value).size()).sum();
        // Emission and short particle lifetimes are random. Await a real rendered bucket,
        // rather than assuming one exists at a single absolute tick, then allow a fresh frame.
        if (count == 0) { staffParticleReadyTime = -1; return false; }
        if (staffParticleReadyTime >= 0 && currentTime > staffParticleReadyTime) return true;
        if (staffParticleReadyTime < 0) staffParticleReadyTime = currentTime;
        return false;
    }

    private static void requireVisibleStaffParticles(PonderScene scene, String stage) throws Exception {
        Object world = scene.getWorld();
        Object particles = requiredField(world, "particles");
        var pending = (Collection<?>) requiredField(particles, "queue");
        var buckets = (Map<?, ?>) requiredField(particles, "byType");
        var byType = new LinkedHashMap<String, Integer>();
        int renderable = 0;
        for (var entry : buckets.entrySet()) {
            int count = ((Collection<?>) entry.getValue()).size();
            byType.put(entry.getKey().toString(), count);
            renderable += count;
        }
        staffParticleAudit.add(Map.of("stage", stage, "sceneTime", scene.getCurrentTime(),
                "pending", pending.size(), "renderable", renderable, "byType", Map.copyOf(byType)));
        if (renderable == 0)
            throw new IllegalStateException("No renderable staff particles at " + stage
                    + ": sceneTime=" + scene.getCurrentTime() + ", pending=" + pending.size());
    }

    private static Object requiredField(Object instance, String name) throws Exception {
        for (Class<?> type = instance.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return Objects.requireNonNull(field.get(instance), name);
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(instance.getClass().getName() + "." + name);
    }

    private static Husk requireStaffTarget(PonderScene scene) {
        var targets = new ArrayList<Husk>();
        scene.forEachWorldEntity(Husk.class, targets::add);
        if (targets.size() != 1) throw new IllegalStateException("Charged staff scene should contain one living Husk target, found " + targets.size());
        return targets.get(0);
    }

    private static int effectStep(GuideBook.Lesson lesson, String effect) {
        for (int i = 0; i < lesson.steps().size(); i++)
            if (lesson.steps().get(i).effect().equals(effect)) return i;
        throw new IllegalStateException("Charged staff lesson is missing effect " + effect);
    }

    private static void advanceTarget(Minecraft client) {
        targetIndex++;
        holdTicks = 0;
        if (targetIndex >= wTargets.size()) {
            finishWAndStartLocales(client);
            return;
        }
        currentTarget = wTargets.get(targetIndex);
        navigationStage = "hold";
    }

    private static void finishWAndStartLocales(Minecraft client) {
        releaseKey();
        client.setScreen(null);
        if (!localeChecksEnabled()) {
            finish(client);
            return;
        }
        localeIndex = 0;
        phase = "locales";
        beginLocaleReload(client, locales.get(localeIndex));
    }

    /** Tracks per-stage progress and fails closed when the client stops advancing through validation. */
    private static void observeProgress(Minecraft client) throws Exception {
        long now = System.nanoTime();
        String stageKey = watchdogStageKey(client);
        boolean stageChanged = !stageKey.equals(watchdogStageKey);
        if (stageChanged) {
            watchdogStageKey = stageKey;
            watchdogStageStartedNanos = now;
        }

        String stateKey = observedStateKey(client, stageKey);
        boolean stateChanged = !stateKey.equals(observedProgressKey);
        if (stateChanged) {
            observedProgressKey = stateKey;
            lastProgressDiagnostic = captureProgressDiagnostic(client);
            recordProgressEvent("state_changed", client);
            logProgressIfUseful(client, stageKey, stateKey, now);
        }

        long timeoutNanos = progressTimeoutNanos();
        long stageAgeNanos = Math.max(0, now - watchdogStageStartedNanos);
        if (stageAgeNanos >= timeoutNanos) {
            var diagnostic = captureProgressDiagnostic(client);
            var timeout = new LinkedHashMap<String, Object>(diagnostic);
            timeout.put("timeoutSeconds", timeoutNanos / 1_000_000_000L);
            timeout.put("stalledSeconds", stageAgeNanos / 1_000_000_000.0);
            timeout.put("stageKey", stageKey);
            failureDiagnostic = Map.copyOf(timeout);
            recordProgressEvent("watchdog_timeout", client);
            saveProgress(client);
            throw new IllegalStateException("Coverage validation made no stage progress for "
                    + (stageAgeNanos / 1_000_000_000L) + " seconds: " + new Gson().toJson(timeout));
        }

        boolean heartbeatDue = now - lastProgressWriteNanos >= PROGRESS_HEARTBEAT_NANOS;
        if (stageChanged || stateChanged || heartbeatDue) {
            lastProgressDiagnostic = captureProgressDiagnostic(client);
            saveProgress(client);
        }
    }

    private static String watchdogStageKey(Minecraft client) {
        String item = currentTarget == null ? "" : currentTarget.itemId();
        String stage = navigationStage == null ? "" : navigationStage;
        String reloadId = reloadTarget == null ? "" : reloadTarget;
        String screenshot = pendingDescriptionScreenshot == null ? "" : pendingDescriptionScreenshot;
        return phase + "|target=" + targetIndex + "|item=" + item + "|stage=" + stage
                + "|locale=" + localeIndex + "|reload=" + reloadId + "|screenshot=" + screenshot;
    }

    private static String observedStateKey(Minecraft client, String stageKey) {
        Object overlay = client.getOverlay();
        Screen screen = client.screen;
        return stageKey + "|screen=" + (screen == null ? "<none>" : screen.getClass().getName())
                + "@" + (screen == null ? 0 : System.identityHashCode(screen))
                + "|overlay=" + (overlay == null ? "<none>" : overlay.getClass().getName())
                + "@" + (overlay == null ? 0 : System.identityHashCode(overlay));
    }

    private static long progressTimeoutNanos() {
        if (phase.equals("native_w"))
            return "staff_wait_replay".equals(navigationStage)
                    ? STAFF_REPLAY_TIMEOUT_NANOS : NATIVE_STAGE_TIMEOUT_NANOS;
        if (phase.equals("locales") || phase.startsWith("restoring_locale"))
            return LOCALE_STAGE_TIMEOUT_NANOS;
        return LOCALE_STAGE_TIMEOUT_NANOS;
    }

    private static void logProgressIfUseful(Minecraft client, String stageKey, String stateKey, long now) {
        boolean phaseChanged = !Objects.equals(lastLoggedPhase, phase);
        boolean specialTarget = currentTarget != null
                && (currentTarget.categoryRepresentative() || currentTarget.energy() != null);
        boolean fifthTarget = phase.equals("native_w") && targetIndex % 5 == 0 && targetIndex != lastSummaryTarget;
        boolean localeMilestone = phase.equals("locales") && (localeIndex % 5 == 0 || pendingDescriptionScreenshot != null);
        boolean specialChanged = specialTarget && !stateKey.equals(lastLoggedSpecialKey);
        if (phaseChanged || fifthTarget || localeMilestone || specialChanged) {
            if (TEXT_ONLY) {
                com.mojang.logging.LogUtils.getLogger().info(
                        "AE2_GUIDE_TEXT_ONLY_PROGRESS: phase={}, locale={}/{}, keysChecked={}, selectedKeys={}, stageAge={}s",
                        phase, localeIndex, locales.size(), localizedKeysChecked,
                        localeIndex < locales.size() ? localeKeys(locales.get(localeIndex)).size() : 0,
                        Math.max(0, now - watchdogStageStartedNanos) / 1_000_000_000L);
            } else {
                com.mojang.logging.LogUtils.getLogger().info(
                        "AE2_GUIDE_ITEM_COVERAGE_PROGRESS: phase={}, target={}/{}, item={}, stage={}, screen={}, "
                                + "holds={}, categories={}, staffRoundTrips={}, locale={}/{}, stageAge={}s",
                        phase, targetIndex + (currentTarget == null ? 0 : 1), wTargets.size(),
                        currentTarget == null ? "" : currentTarget.itemId(), navigationStage,
                        client.screen == null ? "<none>" : client.screen.getClass().getSimpleName(),
                        realHoldChecks, representativeCategoryClicks, chargedStaffCategoryRoundTrips,
                        localeIndex, locales.size(), Math.max(0, now - watchdogStageStartedNanos) / 1_000_000_000L);
            }
            lastLoggedPhase = phase;
            if (fifthTarget) lastSummaryTarget = targetIndex;
            if (specialChanged) lastLoggedSpecialKey = stateKey;
        } else {
            com.mojang.logging.LogUtils.getLogger().debug(
                    "AE2_GUIDE_ITEM_COVERAGE_STATE: {} -> {}", stageKey, stateKey);
        }
    }

    private static void recordProgressEvent(String eventType, Minecraft client) {
        var event = new LinkedHashMap<String, Object>();
        event.put("at", Instant.now().toString());
        event.put("event", eventType);
        event.put("phase", phase);
        event.put("targetIndex", TEXT_ONLY ? "not_run" : targetIndex);
        event.put("targetCount", TEXT_ONLY ? "not_run" : wTargets.size());
        event.put("item", TEXT_ONLY ? "not_run" : currentTarget == null ? "" : currentTarget.itemId());
        event.put("stage", TEXT_ONLY ? "not_run" : navigationStage == null ? "" : navigationStage);
        event.put("stageKey", Objects.toString(watchdogStageKey, ""));
        event.put("stateKey", Objects.toString(observedProgressKey, ""));
        event.put("screen", client.screen == null ? "<none>" : client.screen.getClass().getName());
        if (TEXT_ONLY) {
            event.put("validationCounters", Map.of("status", "not_run"));
        } else {
            event.put("holdTicks", holdTicks);
            event.put("nativeWChecks", realHoldChecks);
            event.put("categories", representativeCategoryClicks);
            event.put("staffRoundTrips", chargedStaffCategoryRoundTrips);
        }
        event.put("localeIndex", localeIndex);
        event.put("stageAgeSeconds", Math.max(0, System.nanoTime() - watchdogStageStartedNanos) / 1_000_000_000.0);
        while (progressEvents.size() >= MAX_PROGRESS_EVENTS) progressEvents.removeFirst();
        progressEvents.addLast(Map.copyOf(event));
    }

    private static Map<String, Object> captureProgressDiagnostic(Minecraft client) {
        var diagnostic = new LinkedHashMap<String, Object>();
        diagnostic.put("capturedAt", Instant.now().toString());
        diagnostic.put("phase", phase);
        diagnostic.put("platform", platform == null ? "" : platform);
        diagnostic.put("targetIndex", TEXT_ONLY ? "not_run" : targetIndex);
        diagnostic.put("targetCount", TEXT_ONLY ? "not_run" : wTargets.size());
        diagnostic.put("item", TEXT_ONLY ? "not_run" : currentTarget == null ? "" : currentTarget.itemId());
        diagnostic.put("navigationStage", TEXT_ONLY ? "not_run" : navigationStage == null ? "" : navigationStage);
        diagnostic.put("holdTicks", TEXT_ONLY ? "not_run" : holdTicks);
        diagnostic.put("reloadTarget", reloadTarget == null ? "" : reloadTarget);
        diagnostic.put("reloadDone", reload == null || reload.isDone());
        diagnostic.put("selectedLocale", client.getLanguageManager() == null
                ? "" : client.getLanguageManager().getSelected());
        if (client.screen instanceof CoverageTooltipScreen tooltip)
            diagnostic.put("tooltipScreenRendered", tooltip.rendered);
        diagnostic.put("screen", client.screen == null ? "<none>" : client.screen.getClass().getName());
        diagnostic.put("screenIdentity", client.screen == null ? 0 : System.identityHashCode(client.screen));
        Object overlay = client.getOverlay();
        diagnostic.put("overlay", overlay == null ? "<none>" : overlay.getClass().getName());
        diagnostic.put("overlayIdentity", overlay == null ? 0 : System.identityHashCode(overlay));
        diagnostic.put("history", screenHistoryDiagnostic());
        diagnostic.put("counters", TEXT_ONLY ? Map.of("status", "registry, factory, W, and staff counters not_run",
                "locales", localeAudit.size(), "localizedKeysChecked", localizedKeysChecked) : Map.of(
                "nativeWChecks", realHoldChecks,
                "factoryChecks", factoryChecks,
                "categoryClicks", representativeCategoryClicks,
                "staffCategoryRoundTrips", chargedStaffCategoryRoundTrips,
                "standardBackChecks", standardBackChecks,
                "localeIndex", localeIndex,
                "localizedKeysChecked", localizedKeysChecked));
        diagnostic.put("sourceHash", fixture == null ? "" : fixture.sourceHash());
        diagnostic.put("inputHashes", Map.copyOf(inputHashes));

        Screen screen = client.screen;
        if (screen instanceof NavigatableSimiScreen navigatable) {
            diagnostic.put("backTrack", backTrackDiagnostic(navigatable));
            diagnostic.put("screenTransition", navigatable.transition.getValue());
        }
        if (screen instanceof PonderUI ui) {
            try {
                PonderScene scene = ui.getActiveScene();
                diagnostic.put("scene", scene.getId().toString());
                diagnostic.put("sceneTime", scene.getCurrentTime());
                diagnostic.put("sceneTotalTime", scene.getTotalTime());
                diagnostic.put("sceneFinished", scene.isFinished());
            } catch (Throwable exception) {
                diagnostic.put("sceneDiagnosticError", exception.toString());
            }
            try { diagnostic.put("replayButton", widgetDiagnostic("replay", ponderButton(ui, "replay"))); }
            catch (Throwable exception) { diagnostic.put("replayButtonDiagnosticError", exception.toString()); }
            try {
                Field field = PonderUI.class.getDeclaredField("tagButtons");
                field.setAccessible(true);
                @SuppressWarnings("unchecked") List<PonderButton> buttons = (List<PonderButton>) field.get(ui);
                diagnostic.put("tagButtons", buttons.stream().map(button -> widgetDiagnostic("category", button)).toList());
            } catch (Throwable exception) {
                diagnostic.put("tagButtonDiagnosticError", exception.toString());
            }
        } else if (screen instanceof PonderTagScreen tagScreen) {
            diagnostic.put("tag", tagScreen.getTag().getId().toString());
            diagnostic.put("tagButtons", screen.children().stream()
                    .filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                    .limit(24).map(button -> widgetDiagnostic("tag-entry", button)).toList());
        }
        return Map.copyOf(diagnostic);
    }

    private static Map<String, Object> screenHistoryDiagnostic() {
        try {
            return Map.of("screens", ScreenOpener.getScreenHistory().stream()
                    .map(screen -> screen.getClass().getName()).toList());
        } catch (Throwable exception) {
            return Map.of("error", exception.toString());
        }
    }

    private static Map<String, Object> backTrackDiagnostic(NavigatableSimiScreen screen) {
        Object value = reflectedField(screen, NavigatableSimiScreen.class, "backTrack");
        if (value == null) return Map.of("present", false);
        if (value instanceof AbstractWidget widget) return widgetDiagnostic("back", widget);
        return Map.of("present", true, "class", value.getClass().getName());
    }

    private static Map<String, Object> widgetDiagnostic(String role, AbstractWidget widget) {
        var state = new LinkedHashMap<String, Object>();
        state.put("role", role);
        state.put("class", widget.getClass().getName());
        state.put("message", widget.getMessage().getString());
        state.put("visible", widget.visible);
        state.put("active", widget.active);
        state.put("x", widget.getX());
        state.put("y", widget.getY());
        state.put("width", widget.getWidth());
        state.put("height", widget.getHeight());
        if (widget instanceof PonderButton button) {
            try { state.put("fade", button.fade().getValue()); }
            catch (Throwable exception) { state.put("fadeError", exception.toString()); }
            try {
                ItemStack item = button.getItem();
                state.put("item", item.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(item.getItem()).toString());
            } catch (Throwable ignored) { }
        } else {
            Object fade = reflectedFadeValue(widget);
            if (fade != null) state.put("fade", fade);
        }
        return Map.copyOf(state);
    }

    private static Object reflectedFadeValue(Object object) {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                String fieldName = field.getName().toLowerCase(Locale.ROOT);
                if (!fieldName.contains("fade") && !fieldName.contains("alpha")) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(object);
                    if (value instanceof Number) return value;
                    if (value == null) continue;
                    Method getter = value.getClass().getMethod("getValue");
                    return getter.invoke(value);
                } catch (Throwable ignored) { }
            }
        }
        return null;
    }

    private static Object reflectedField(Object instance, Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(instance);
        } catch (Throwable exception) {
            return null;
        }
    }

    private static Map<String, Object> progressDetails(Minecraft client, long now) {
        var details = new LinkedHashMap<String, Object>();
        details.put("file", PROGRESS_FILE);
        details.put("stageKey", Objects.toString(watchdogStageKey, ""));
        details.put("stateKey", Objects.toString(observedProgressKey, ""));
        details.put("stageAgeSeconds", watchdogStageKey == null ? 0
                : Math.max(0, now - watchdogStageStartedNanos) / 1_000_000_000.0);
        details.put("timeoutSeconds", progressTimeoutNanos() / 1_000_000_000L);
        details.put("lastDiagnostic", lastProgressDiagnostic);
        details.put("recentTransitions", List.copyOf(progressEvents));
        details.put("failureDiagnostic", failureDiagnostic);
        return Map.copyOf(details);
    }

    private static void saveProgress(Minecraft client) throws Exception {
        long now = System.nanoTime();
        var progress = new LinkedHashMap<String, Object>();
        progress.put("mode", TEXT_ONLY ? "text_only_progress" : "item_coverage_progress");
        progress.put("updatedAt", Instant.now().toString());
        progress.put("phase", phase);
        progress.put("finished", finished);
        progress.put("passed", passed && finished);
        progress.put("platform", platform == null ? "" : platform);
        progress.put("sourceHash", fixture == null ? "" : fixture.sourceHash());
        progress.put("inputHashes", Map.copyOf(inputHashes));
        progress.put("targetIndex", TEXT_ONLY ? "not_run" : targetIndex);
        progress.put("targetCount", TEXT_ONLY ? "not_run" : wTargets.size());
        progress.put("item", TEXT_ONLY ? "not_run" : currentTarget == null ? "" : currentTarget.itemId());
        progress.put("navigationStage", TEXT_ONLY ? "not_run" : navigationStage == null ? "" : navigationStage);
        progress.put("counts", TEXT_ONLY ? Map.of("status", "registry, factory, W, and staff counters not_run",
                "locales", localeAudit.size(), "localizedKeysChecked", localizedKeysChecked) : Map.of(
                "nativeWChecks", realHoldChecks,
                "factoryChecks", factoryChecks,
                "categoryClicks", representativeCategoryClicks,
                "staffCategoryRoundTrips", chargedStaffCategoryRoundTrips,
                "standardBackChecks", standardBackChecks,
                "locales", localeAudit.size(),
                "localizedKeysChecked", localizedKeysChecked));
        progress.put("progress", progressDetails(client, now));
        if (failure != null) progress.put("failure", failure);
        Path path = client.gameDirectory.toPath().resolve(PROGRESS_FILE);
        Files.writeString(path, new Gson().toJson(progress));
        lastProgressWriteNanos = now;
    }

    private static void saveProgressQuietly(Minecraft client) {
        try { saveProgress(client); }
        catch (Throwable exception) {
            com.mojang.logging.LogUtils.getLogger().error("Could not write AE2 item coverage progress JSON", exception);
        }
    }

    private static void tickLocales(Minecraft client) throws Exception {
        if (client.getOverlay() != null) return;
        if (reload != null) return;
        if (pendingDescriptionScreenshot != null) {
            if (!(client.screen instanceof CoverageTooltipScreen tooltip))
                throw new IllegalStateException("Description screenshot screen was replaced before rendering");
            if (!tooltip.rendered) return;
            captureDescriptionScreenshot(client);
            beginNextLocaleOrRestore(client);
            return;
        }
        if (localeIndex >= locales.size()) {
            restoreLocale(client);
            return;
        }
        String locale = locales.get(localeIndex);
        verifyLocale(client, locale);
        localeIndex++;
        if (!queueDescriptionScreenshot(client, locale)) beginNextLocaleOrRestore(client);
    }

    private static void beginLocaleReload(Minecraft client, String locale) {
        if (client.getOverlay() != null)
            throw new IllegalStateException("Cannot begin item coverage locale reload while a client overlay is active");
        reloadTarget = locale;
        if (locale.equals(client.getLanguageManager().getSelected())) {
            reload = CompletableFuture.completedFuture(null);
            return;
        }
        client.getLanguageManager().setSelected(locale);
        client.options.languageCode = locale;
        reload = client.reloadResourcePacks();
    }

    private static void tickReload(Minecraft client) throws Exception {
        if (!reload.isDone() || client.getOverlay() != null) return;
        reload.join();
        reload = null;
        if (reloadTarget.equals("restore:" + originalLocale)) {
            if (!originalLocale.equals(client.getLanguageManager().getSelected()))
                throw new IllegalStateException("Item coverage validation did not restore the selected locale");
            finish(client);
            return;
        }
        if (!reloadTarget.equals(client.getLanguageManager().getSelected()))
            throw new IllegalStateException("Requested locale reload selected the wrong language: expected=" + reloadTarget
                    + ", actual=" + client.getLanguageManager().getSelected());
        String locale = reloadTarget;
        verifyLocale(client, locale);
        localeIndex++;
        if (!queueDescriptionScreenshot(client, locale)) beginNextLocaleOrRestore(client);
    }

    private static boolean queueDescriptionScreenshot(Minecraft client, String locale) {
        String itemId = DESCRIPTION_SCREENSHOT_ITEMS.get(locale);
        if (itemId == null) return false;
        ResourceLocation location = exactId(itemId);
        var item = BuiltInRegistries.ITEM.getOptional(location)
                .orElseThrow(() -> new IllegalStateException("Description screenshot item is not registered: " + itemId));
        var stack = new ItemStack(item);
        var entry = GuideItemDescriptions.descriptionFor(stack);
        if (entry == null)
            throw new IllegalStateException("Description screenshot item has no active source entry: " + itemId);
        if (!effectiveLocaleKeys().contains(entry.translationKey())) {
            if (TEXT_ONLY || scopedTextMode()) return false;
            throw new IllegalStateException("Description screenshot does not target a changed description: " + itemId);
        }
        if (!locale.equals(Minecraft.getInstance().getLanguageManager().getSelected()))
            throw new IllegalStateException("Description screenshot locale is not active: " + locale);
        expectedDescriptionScreenshotCount++;
        pendingDescriptionScreenshot = locale;
        client.setScreen(new CoverageTooltipScreen(locale, stack));
        return true;
    }

    private static void captureDescriptionScreenshot(Minecraft client) {
        String locale = pendingDescriptionScreenshot;
        String itemId = DESCRIPTION_SCREENSHOT_ITEMS.get(locale);
        var entry = GuideItemDescriptions.descriptionFor(
                new ItemStack(BuiltInRegistries.ITEM.get(exactId(itemId))));
        if (entry == null) throw new IllegalStateException("Description disappeared before screenshot: " + itemId);
        GuideValidation.screenshot("item-coverage-description-" + locale + ".png");
        descriptionScreenshotAudit.add(Map.of("locale", locale, "item", itemId,
                "translationKey", entry.translationKey(),
                "renderedFrame", true));
        client.setScreen(null);
        pendingDescriptionScreenshot = null;
    }

    private static void beginNextLocaleOrRestore(Minecraft client) {
        if (localeIndex < locales.size()) beginLocaleReload(client, locales.get(localeIndex));
        else restoreLocale(client);
    }

    private static void restoreLocale(Minecraft client) {
        if (originalLocale.equals(client.getLanguageManager().getSelected())) {
            finish(client);
            return;
        }
        reloadTarget = "restore:" + originalLocale;
        client.getLanguageManager().setSelected(originalLocale);
        client.options.languageCode = originalOptionLanguage;
        reload = client.reloadResourcePacks();
    }

    private static void finish(Minecraft client) {
        client.options.languageCode = originalOptionLanguage;
        client.options.guiScale().set(originalGuiScale);
        client.resizeDisplay();
        client.setScreen(returnScreen);
        if (failure == null) phase = "complete";
        if (TEXT_ONLY) {
            passed = failuresAbsent() && localeAudit.size() == expectedLocaleCount()
                    && localizedKeysChecked == expectedTextKeyCheckCount()
                    && overflows.isEmpty()
                    && expandedDescriptionChecks == expectedDescriptionExpansionCount()
                    && localizedSearchLocalesChecked == expectedCaptionLocaleCount()
                    && (textSelection == null || localizedSearchTermsChecked == expectedSelectedCaptionSearchTerms)
                    && localizedIndexedCaptionsChecked == expectedIndexedCaptionCount()
                    && pendingDescriptionScreenshot == null
                    && descriptionScreenshotAudit.size() == expectedDescriptionScreenshotCount;
        } else if (STAFF_ONLY) {
            boolean localeChecksPassed = !localeChecksEnabled()
                    ? localeAudit.isEmpty() && descriptionScreenshotAudit.isEmpty()
                    : localeAudit.size() == 22
                        && localizedKeysChecked == effectiveLocaleKeys().size() * 22
                        && overflows.isEmpty()
                        && expandedDescriptionChecks == effectiveLocaleKeys().stream()
                                .filter(key -> key.startsWith("ae2ponder.description.")).count() * 22
                        && descriptionScreenshotAudit.size() == expectedDescriptionScreenshotCount;
            passed = failuresAbsent() && realHoldChecks == wTargets.size()
                    && representativeCategoryClicks == 3 && chargedStaffCategoryRoundTrips == 3
                    && chargedStaffDataEquivalenceChecked && standardBackChecks >= wTargets.size() + 3
                    && staffSceneScreenshots.size() == 5 && staffParticleAudit.size() == 4
                    && pendingDescriptionScreenshot == null && localeChecksPassed;
        } else {
            passed = failuresAbsent() && localeAudit.size() == 22 && realHoldChecks == wTargets.size()
                    && representativeCategoryClicks == selected.cableFamilies().size() + 3
                    && chargedStaffCategoryRoundTrips == 3 && chargedStaffDataEquivalenceChecked
                    && standardBackChecks >= wTargets.size()
                    && descriptionScreenshotAudit.size() == DESCRIPTION_SCREENSHOT_ITEMS.size()
                    && pendingDescriptionScreenshot == null;
        }
        finished = true;
        recordProgressEvent("finished", client);
        saveProgressQuietly(client);
    }

    private static void verifyLocale(Minecraft client, String locale) throws Exception {
        var own = readLanguage(locale);
        int localeChanged = 0;
        int localeCaptions = 0;
        int localeDescriptions = 0;
        int localeExpandedDescriptions = 0;
        int localeSkippedDescriptionExpansions = 0;
        List<Map<String, Object>> localeOverflows = new ArrayList<>();
        List<String> localeKeys = localeKeys(locale);
        for (String key : localeKeys) {
            String raw = own.get(key);
            if (raw == null || raw.isBlank())
                throw new IllegalStateException("Changed source key is absent in " + locale + ": " + key);
            String merged = net.minecraft.locale.Language.getInstance().getOrDefault(key);
            if (!merged.equals(raw) || !Component.translatable(key).getString().equals(raw))
                throw new IllegalStateException("Changed source key fell back or changed in the runtime language stack: "
                        + locale + "/" + key + ", own=" + raw + ", merged=" + merged);
            localeChanged++;
            if (GuideCaptionValidation.isCaptionKey(key)) {
                verifyCaptionRoles(locale, key, raw);
                int count = verifyCaption(client.font, locale, key, raw, localeOverflows);
                changedCaptionUnits += count;
                localeCaptions += count;
            }
            if (key.startsWith("ae2ponder.description.")) {
                GuideItemDescriptions.Entry entry = GuideItemDescriptions.descriptions().stream()
                        .filter(value -> value.translationKey().equals(key)).findFirst().orElse(null);
                if (entry == null) {
                    if (!isOptionalDescriptionUnavailable(key))
                        throw new IllegalStateException("Changed description key has no active source entry: " + key);
                    verifyDescriptionSource(client.font, locale, key, raw, localeOverflows);
                    localeDescriptions++;
                    localeSkippedDescriptionExpansions++;
                    continue;
                }
                if (verifyExpandedTooltip(client, entry, raw, locale, localeOverflows)) {
                    localeExpandedDescriptions++;
                    expandedDescriptionChecks++;
                } else {
                    localeSkippedDescriptionExpansions++;
                }
                localeDescriptions++;
            }
        }
        if (localeChanged != localeKeys.size())
            throw new IllegalStateException("Not all changed source keys were checked in " + locale);
        if (localeDescriptions != localeKeys.stream().filter(key -> key.startsWith("ae2ponder.description.")).count())
            throw new IllegalStateException("Changed Shift descriptions were not all expanded in " + locale);
        if (!TEXT_ONLY && !scopedTextMode()) verifyStaffLocalizedTarget(locale);
        RuntimeItemNameAudit itemNames = TEXT_ONLY ? resolveRuntimeItemNames()
                : new RuntimeItemNameAudit(Map.of(), List.of());
        int searchTerms = TEXT_ONLY ? verifySelectedCaptionSearch(locale, own) : 0;
        if (TEXT_ONLY && localeKeys.stream().anyMatch(GuideCaptionValidation::isCaptionKey)) {
            if (searchTerms == 0)
                throw new IllegalStateException("TextOnly selected captions but checked no localized GuideME search terms: " + locale);
            localizedSearchLocalesChecked++;
            localizedSearchTermsChecked += searchTerms;
        }
        overflows.addAll(localeOverflows);
        // Collect every locale's layout errors in one run; finish() still fails if any overflow exists.
        var localeRecord = new LinkedHashMap<String, Object>();
        localeRecord.put("locale", locale);
        localeRecord.put("changedKeys", localeChanged);
        localeRecord.put("captionUnits", localeCaptions);
        localeRecord.put("expandedDescriptions", localeExpandedDescriptions);
        localeRecord.put("optionalDescriptionSkips", localeSkippedDescriptionExpansions);
        localeRecord.put("allChangedKeysUseOwnLocale", true);
        localeRecord.put("overflows", localeOverflows.size());
        if (TEXT_ONLY) {
            localeRecord.put("localizedCaptionSearchTerms", searchTerms);
            localeRecord.put("selectedTextKeys", localeKeys);
            localeRecord.put("completeIndexedCaptions", localeKeys.stream()
                    .filter(GuideCaptionValidation::isCaptionKey).count());
            localeRecord.put("runtimeItemNames", itemNames.names());
            localeRecord.put("runtimeItemNamesUnavailable", itemNames.unavailable());
        }
        localeAudit.add(Map.copyOf(localeRecord));
        localizedKeysChecked += localeChanged;
    }

    private static int verifySelectedCaptionSearch(String locale, Map<String, String> own) throws Exception {
        List<String> captionKeys = localeKeys(locale).stream()
                .filter(GuideCaptionValidation::isCaptionKey).toList();
        if (captionKeys.isEmpty()) return 0;
        var guide = Guides.getById(GUIDE_ID);
        if (guide == null) throw new IllegalStateException("GuideME did not load the guide for localized caption search");
        String indexedLocale = guide.getParsedPage(GUIDE_SEARCH_PAGE).getLanguage();
        if (!locale.equals(indexedLocale))
            throw new IllegalStateException("GuideME caption search page did not reload the selected locale: expected="
                    + locale + ", actual=" + indexedLocale);

        GuideSearch search = GuideMEClient.instance().getSearch();
        search.index(guide);
        Field pendingField = GuideSearch.class.getDeclaredField("pendingTasks");
        pendingField.setAccessible(true);
        for (int pass = 0; pass < 500; pass++) {
            search.processWork();
            if (((Collection<?>) pendingField.get(search)).isEmpty()) break;
        }
        if (!((Collection<?>) pendingField.get(search)).isEmpty())
            throw new IllegalStateException("GuideME caption search indexing did not finish: " + locale);

        int termsChecked = 0;
        for (String key : captionKeys) {
            String rawCaption = own.get(key);
            if (rawCaption == null || rawCaption.isBlank())
                throw new IllegalStateException("Selected caption search source is missing in " + locale + ": " + key);
            String fullQuery = GuideCaptionValidation.normalizeForSearch(rawCaption);
            verifyIndexedCaptionText(guide, fullQuery, locale, key + " full caption");
            localizedIndexedCaptionsChecked++;
            List<String> units = dev.ae2guide.client.CaptionPages.split(rawCaption);
            if (units.isEmpty()) throw new IllegalStateException("Selected caption has no authored search units: " + locale + "/" + key);
            for (int unitIndex = 0; unitIndex < units.size(); unitIndex++) {
                String query = units.get(unitIndex);
                // Leading/trailing ellipses mark authored continuation boundaries, not the searchable prose.
                if (unitIndex > 0 && query.startsWith("…")) query = query.substring(1).stripLeading();
                if (unitIndex + 1 < units.size() && query.endsWith("…"))
                    query = query.substring(0, query.length() - 1).stripTrailing();
                query = GuideCaptionValidation.normalizeForSearch(query);
                if (query.isBlank()) throw new IllegalStateException("Selected caption has an empty search unit: " + locale + "/" + key);
                requireGuideSearchResult(search, guide, query, locale, key + " unit " + (unitIndex + 1));
                termsChecked++;
            }
        }
        return termsChecked;
    }

    private static void verifyIndexedCaptionText(guideme.Guide guide, String expected, String locale, String source) {
        final String searchable;
        try {
            var page = guide.getParsedPage(GUIDE_SEARCH_PAGE);
            var indexText = GuideSearch.class.getDeclaredMethod("getSearchableText",
                    guideme.Guide.class, guideme.compiler.ParsedGuidePage.class);
            indexText.setAccessible(true);
            searchable = (String) indexText.invoke(null, guide, page);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not inspect GuideME indexed caption text: locale=" + locale
                    + ", source=" + source, exception);
        }
        if (!searchable.contains(expected))
            throw new IllegalStateException("GuideME indexed text lost selected localized caption: locale=" + locale
                    + ", source=" + source + ", expected=" + expected);
    }

    private static void requireGuideSearchResult(GuideSearch search, guideme.Guide guide,
                                                 String query, String locale, String source) {
        final boolean found;
        try {
            found = search.searchGuide(query, guide).stream()
                    .anyMatch(result -> GUIDE_SEARCH_PAGE.equals(result.pageId()));
        } catch (RuntimeException exception) {
            throw new IllegalStateException("GuideME standard search threw for selected caption text: locale="
                    + locale + ", source=" + source + ", query=" + query, exception);
        }
        if (!found)
            throw new IllegalStateException("GuideME standard search missed selected caption text: locale="
                    + locale + ", source=" + source + ", query=" + query);
    }

    private static RuntimeItemNameAudit resolveRuntimeItemNames() {
        var names = new LinkedHashMap<String, String>();
        var unavailable = new ArrayList<String>();
        for (String rawId : RUNTIME_NAME_ITEM_IDS) {
            ResourceLocation id = exactId(rawId);
            if (!Platform.isModLoaded(id.getNamespace())) {
                unavailable.add(rawId + " (namespace not loaded)");
                continue;
            }
            var item = BuiltInRegistries.ITEM.getOptional(id)
                    .orElseThrow(() -> new IllegalStateException("Runtime-name target is missing despite its loaded namespace: " + rawId));
            String displayName = new ItemStack(item).getHoverName().getString();
            if (displayName.isBlank())
                throw new IllegalStateException("Runtime item display name is empty: " + rawId);
            names.put(rawId, displayName);
        }
        return new RuntimeItemNameAudit(Map.copyOf(names), List.copyOf(unavailable));
    }

    private static int verifyCaption(Font font, String locale, String key, String text,
                                     List<Map<String, Object>> localeOverflows) {
        List<String> units = CaptionPages.split(text);
        if (units.isEmpty() || !String.join("\n", units).equals(text))
            throw new IllegalStateException("Caption authored boundaries were changed: " + locale + "/" + key);
        for (int unitIndex = 0; unitIndex < units.size(); unitIndex++) {
            String unit = units.get(unitIndex);
            if (unit.isBlank()) throw new IllegalStateException("Caption has an empty authored unit: " + locale + "/" + key);
            List<FormattedText> lines = wrap(font, locale, unit, GuideTextLayout.CAPTION_WIDTH);
            int width = lines.stream().mapToInt(line -> renderedWidth(font, locale, line.getString())).max().orElse(0);
            String joined = lines.stream().map(FormattedText::getString).collect(java.util.stream.Collectors.joining())
                    .replaceAll("\\s+", "");
            if (!joined.equals(unit.replaceAll("\\s+", "")) || lines.size() > CaptionPages.MAX_LINES
                    || width > GuideTextLayout.CAPTION_WIDTH) {
                localeOverflows.add(Map.of("key", key, "unit", unitIndex, "width", width,
                        "lines", lines.size(), "text", unit));
            }
        }
        return units.size();
    }

    private static boolean verifyExpandedTooltip(Minecraft client, GuideItemDescriptions.Entry entry, String expected,
                                                 String locale, List<Map<String, Object>> localeOverflows) {
        ItemStack stack = entry.items().stream().map(GuideResources::parse)
                .filter(BuiltInRegistries.ITEM::containsKey).map(id -> new ItemStack(BuiltInRegistries.ITEM.get(id)))
                .findFirst().orElse(null);
        int widthLimit = Math.max(1, Math.min(240, client.getWindow().getGuiScaledWidth() - 32));
        String[] units = expected.split("\\n", -1);
        if (Arrays.stream(units).anyMatch(String::isBlank))
            throw new IllegalStateException("Expanded Shift description has an empty authored unit: " + locale + "/" + entry.id());
        if (entry.hasExplicitTones() && entry.tones().size() != units.length)
            throw new IllegalStateException("Item description roles differ from authored units: " + locale + "/" + entry.id());
        if (stack == null) {
            if (!isForgeDescriptionWithoutItem(entry.id()))
                throw new IllegalStateException("No registered target stack for description " + entry.id());
            verifyDescriptionSource(client.font, locale, entry.translationKey(), expected, localeOverflows);
            return false;
        }
        var originalName = stack.getHoverName();
        var originalRemainder = Component.literal("Coverage validation remainder");
        var expanded = new ArrayList<Component>(List.of(originalName, originalRemainder));
        GuideItemDescriptions.append(stack, expanded, true);
        if (expanded.get(0) != originalName || expanded.get(expanded.size() - 1) != originalRemainder)
            throw new IllegalStateException("Expanded Shift tooltip replaced existing lines: " + entry.id());
        var lines = expanded.subList(1, expanded.size() - 1);
        String rendered = lines.stream().map(Component::getString).collect(java.util.stream.Collectors.joining())
                .replaceAll("\\s+", "");
        String shaped = dev.ae2guide.client.RightToLeftText.shapeArabic(expected).replaceAll("\\s+", "");
        if (!rendered.equals(shaped))
            throw new IllegalStateException("Expanded Shift tooltip lost localized content: " + locale + "/" + entry.id());
        int offset = 0;
        for (int unitIndex = 0; unitIndex < units.length; unitIndex++) {
            String shapedUnit = dev.ae2guide.client.RightToLeftText.shapeArabic(units[unitIndex]);
            List<FormattedText> expectedLines = wrap(client.font, locale, shapedUnit, widthLimit);
            int actualEnd = offset + expectedLines.size();
            if (actualEnd > lines.size())
                throw new IllegalStateException("Expanded Shift tooltip omitted wrapped lines: " + locale + "/" + entry.id()
                        + " unit=" + unitIndex);
            var actualLines = lines.subList(offset, actualEnd);
            String expectedUnitText = expectedLines.stream().map(FormattedText::getString)
                    .collect(java.util.stream.Collectors.joining());
            String actualUnitText = actualLines.stream().map(Component::getString)
                    .collect(java.util.stream.Collectors.joining());
            int maxWidth = actualLines.stream().mapToInt(line -> renderedWidth(client.font, locale, line.getString())).max().orElse(0);
            CaptionTone expectedTone = entry.hasExplicitTones() ? entry.tones().get(unitIndex) : entry.tone();
            boolean wrongTone = actualLines.stream().anyMatch(line -> line.getStyle().getColor() == null
                    || line.getStyle().getColor().getValue() != expectedTone.textColor());
            if (!withoutWhitespace(expectedUnitText).equals(withoutWhitespace(actualUnitText)))
                throw new IllegalStateException("Expanded Shift tooltip changed authored unit text: " + locale + "/"
                        + entry.id() + " unit=" + unitIndex);
            if (wrongTone)
                throw new IllegalStateException("Expanded Shift tooltip used the wrong authored tone: " + locale + "/"
                        + entry.id() + " unit=" + unitIndex);
            if (expectedLines.isEmpty() || expectedLines.size() > 3 || maxWidth > widthLimit)
                localeOverflows.add(Map.of("key", entry.translationKey(), "unit", unitIndex, "width", maxWidth,
                        "lines", expectedLines.size(), "text", units[unitIndex]));
            offset = actualEnd;
        }
        if (offset != lines.size())
            throw new IllegalStateException("Expanded Shift tooltip produced lines outside the authored units: "
                    + locale + "/" + entry.id());
        var collapsed = new ArrayList<Component>(List.of(originalName, originalRemainder));
        GuideItemDescriptions.append(stack, collapsed, false);
        if (collapsed.size() != 3 || collapsed.get(0) != originalName || collapsed.get(2) != originalRemainder
                || collapsed.get(1).getString().isBlank() || collapsed.get(1).getStyle().getColor() == null
                || collapsed.get(1).getStyle().getColor().getValue() != CaptionTone.HINT.textColor())
            throw new IllegalStateException("Collapsed Shift hint did not preserve the original tooltip: " + entry.id());
        return true;
    }

    private static void verifyDescriptionSource(Font font, String locale, String key, String text,
                                                List<Map<String, Object>> localeOverflows) {
        String[] units = text.split("\\n", -1);
        if (Arrays.stream(units).anyMatch(String::isBlank))
            throw new IllegalStateException("Localized Shift description has an empty authored unit: " + locale + "/" + key);
        for (int unitIndex = 0; unitIndex < units.length; unitIndex++) {
            String shaped = dev.ae2guide.client.RightToLeftText.shapeArabic(units[unitIndex]);
            List<FormattedText> lines = wrap(font, locale, shaped, 240);
            String joined = lines.stream().map(FormattedText::getString)
                    .collect(java.util.stream.Collectors.joining());
            int width = lines.stream().mapToInt(line -> renderedWidth(font, locale, line.getString())).max().orElse(0);
            if (!withoutWhitespace(joined).equals(withoutWhitespace(shaped)))
                throw new IllegalStateException("Localized Shift description font wrapping changed source text: "
                        + locale + "/" + key + " unit=" + unitIndex);
            if (lines.isEmpty() || lines.size() > 3 || width > 240)
                localeOverflows.add(Map.of("key", key, "unit", unitIndex, "width", width,
                        "lines", lines.size(), "text", units[unitIndex]));
        }
    }

    private static boolean isOptionalDescriptionUnavailable(String translationKey) {
        String descriptionId = translationKey.substring("ae2ponder.description.".length());
        return selected.expectedNewEntrances().entrySet().stream()
                .filter(entry -> Objects.equals(entry.getValue().description(), descriptionId))
                .anyMatch(entry -> !Platform.isModLoaded(namespace(entry.getKey())));
    }

    private static boolean isForgeDescriptionWithoutItem(String id) {
        return "forge".equals(platform) && FORGE_DESCRIPTIONS_WITHOUT_ITEMS.contains(id);
    }

    private static void verifyCaptionRoles(String locale, String key, String caption) {
        Map<String, List<String>> localeRoles = captionRoles.get(locale);
        List<String> expected = localeRoles == null ? null : localeRoles.get(key);
        int unitCount = caption.split("\\n", -1).length;
        if (expected == null || expected.size() != unitCount)
            throw new IllegalStateException("Caption role resource does not match changed authored units: "
                    + locale + "/" + key);
        List<String> runtime = GuideCaptionTones.forLocale(locale, key, unitCount).stream()
                .map(Enum::name).toList();
        if (!runtime.equals(expected))
            throw new IllegalStateException("Compiled caption roles differ from their resource data: " + locale + "/" + key);
    }

    private static void verifyStaffLocalizedTarget(String locale) {
        GuideBook.Lesson lesson = GuideBook.lessons().stream().filter(value -> value.id().equals("charged_staff"))
                .findFirst().orElseThrow(() -> new IllegalStateException("Charged staff lesson was filtered out"));
        String title = Component.translatable("ae2ponder.ponder.charged_staff.header").getString();
        var commandTarget = GuideNavigation.firstLessonForItem(STAFF_ID);
        var chapterCommandTarget = GuideNavigation.firstLessonForItem(GuideResources.parse(STAFF_ID), lesson.chapter());
        if (title.isBlank() || commandTarget == null || !commandTarget.id().equals(lesson.id())
                || chapterCommandTarget == null || !chapterCommandTarget.id().equals(lesson.id()))
            throw new IllegalStateException("Localized charged staff title or item command does not resolve to its lesson in " + locale);
    }

    private static List<String> readLanguageIds() throws Exception {
        Map<String, Object> languages = read(GuideResources.of("ae2ponder", "guide/languages.json"), Map.class);
        List<String> result = languages.keySet().stream().sorted().toList();
        if (result.size() != 22) throw new IllegalStateException("Expected 22 supported locales, found " + result.size());
        return result;
    }

    private static Map<String, String> readLanguage(String locale) throws Exception {
        String path = "/assets/ae2ponder/lang/" + locale + ".json";
        byte[] bytes = readBytes(path);
        inputHashes.put("locale_resource:" + locale, sha256(bytes));
        try (var reader = new InputStreamReader(new java.io.ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            return new Gson().fromJson(reader, Map.class);
        }
    }

    private static <T> T read(ResourceLocation id, Class<T> type) throws Exception {
        String path = "/assets/" + id.getNamespace() + "/" + id.getPath();
        return read(path, type);
    }

    private static <T> T read(String path, Class<T> type) throws Exception {
        try (var reader = new InputStreamReader(new java.io.ByteArrayInputStream(readBytes(path)), StandardCharsets.UTF_8)) {
            return new Gson().fromJson(reader, type);
        }
    }

    private static LocaleTextSelection readTextSelection(String path) throws Exception {
        try (var reader = new JsonReader(new InputStreamReader(
                new java.io.ByteArrayInputStream(readBytes(path)), StandardCharsets.UTF_8))) {
            reader.beginObject();
            String selectedPlatform = null;
            Map<String, List<String>> selectedLocales = null;
            Set<String> fields = new LinkedHashSet<>();
            while (reader.hasNext()) {
                String field = reader.nextName();
                if (!fields.add(field)) throw new IllegalStateException("TextSelection contains a duplicate field: " + field);
                if (field.equals("platform")) {
                    selectedPlatform = reader.nextString();
                } else if (field.equals("locales")) {
                    selectedLocales = new LinkedHashMap<>();
                    reader.beginObject();
                    while (reader.hasNext()) {
                        String locale = reader.nextName();
                        if (selectedLocales.containsKey(locale))
                            throw new IllegalStateException("TextSelection contains a duplicate locale: " + locale);
                        List<String> keys = new ArrayList<>();
                        reader.beginArray();
                        while (reader.hasNext()) keys.add(reader.nextString());
                        reader.endArray();
                        selectedLocales.put(locale, List.copyOf(keys));
                    }
                    reader.endObject();
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT)
                throw new IllegalStateException("TextSelection has trailing JSON data");
            return new LocaleTextSelection(selectedPlatform,
                    selectedLocales == null ? null : Map.copyOf(selectedLocales));
        }
    }

    private static byte[] readBytes(String path) throws Exception {
        try (var stream = Objects.requireNonNull(GuideItemCoverageValidation.class.getResourceAsStream(path),
                "Missing item coverage validation resource: " + path)) {
            return stream.readAllBytes();
        }
    }

    private static void captureInputHashes() throws Exception {
        for (String path : List.of(FIXTURE,
                "/assets/ae2ponder/guide/item_descriptions.json",
                "/assets/ae2ponder/guide/lessons.json",
                "/assets/ae2ponder/guide/addons.json")) {
            try (var stream = Objects.requireNonNull(GuideItemCoverageValidation.class.getResourceAsStream(path), path)) {
                inputHashes.put(path, sha256(stream.readAllBytes()));
            }
        }
        if (textSelectionResourcePath != null)
            inputHashes.put(textSelectionResourcePath, sha256(readBytes(textSelectionResourcePath)));
        String captionRolesPath = "/assets/ae2ponder/guide/caption_styles.json";
        byte[] captionRolesBytes = readBytes(captionRolesPath);
        inputHashes.put(captionRolesPath, sha256(captionRolesBytes));
        var captionRolesType = new TypeToken<Map<String, Map<String, List<String>>>>() {}.getType();
        try (var reader = new InputStreamReader(new java.io.ByteArrayInputStream(captionRolesBytes), StandardCharsets.UTF_8)) {
            captionRoles = new Gson().fromJson(reader, captionRolesType);
        }
        if (captionRoles == null || captionRoles.size() != 22)
            throw new IllegalStateException("Compiled caption role resource must contain all 22 locales");
        for (String locale : new TreeSet<>(captionRoles.keySet())) {
            var canonicalRoles = new LinkedHashMap<String, List<String>>();
            new TreeMap<>(captionRoles.get(locale)).forEach(canonicalRoles::put);
            inputHashes.put("caption_roles:" + locale,
                    sha256(new Gson().toJson(canonicalRoles).getBytes(StandardCharsets.UTF_8)));
        }
        for (Class<?> type : List.of(GuideItemCoverageValidation.class, GuideCaptionValidation.class,
                GuideValidation.class, GuideUiValidation.class, ValidationPlatform.class, GuidePlugin.class,
                GuideBook.class, GuideScenes.class,
                GuideItemDescriptions.class, GuideCaptionTones.class, CaptionTone.class,
                GuideNavigation.class, GuideTextLayout.class,
                dev.ae2guide.client.CaptionPages.class, dev.ae2guide.client.RightToLeftText.class,
                GuideResources.class, Platform.class, PonderUIAccessor.class)) hashClass(type);
        inputHashes.put("dev.ae2guide.mixin.PonderNavigationOriginMixin",
                sha256(readBytes("/dev/ae2guide/mixin/PonderNavigationOriginMixin.class")));
        inputHashes.put("dev.ae2guide.mixin.GuideSearchQueryMixin",
                sha256(readBytes("/dev/ae2guide/mixin/GuideSearchQueryMixin.class")));
        hashClass(GuideSearch.class);
        hashClass(Class.forName("dev.ae2guide.client.GuideToolScenes", false,
                GuideItemCoverageValidation.class.getClassLoader()));
        hashClass(Class.forName("dev.ae2guide.client.GuideBook$Lesson", false,
                GuideItemCoverageValidation.class.getClassLoader()));
        hashClass(Class.forName("dev.ae2guide.client.GuideItemDescriptions$Entry", false,
                GuideItemCoverageValidation.class.getClassLoader()));
    }

    private static void hashClass(Class<?> type) throws Exception {
        String path = "/" + type.getName().replace('.', '/') + ".class";
        try (var stream = Objects.requireNonNull(type.getResourceAsStream(path), path)) {
            inputHashes.put(type.getName(), sha256(stream.readAllBytes()));
        }
    }

    private static void fail(Minecraft client, Throwable exception) {
        String failedPhase = phase;
        Map<String, Object> failureState = captureProgressDiagnostic(client);
        if (failureDiagnostic.isEmpty()) failureDiagnostic = failureState;
        else {
            var combined = new LinkedHashMap<>(failureDiagnostic);
            combined.put("stateAtFailure", failureState);
            failureDiagnostic = Map.copyOf(combined);
        }
        String detail = exception.getClass().getName() + ": " + Objects.toString(exception.getMessage(), "");
        if (failure == null) failure = "during " + failedPhase + ": " + detail;
        else failure += " | locale restoration failed: " + detail;
        phase = "failed";
        passed = false;
        try { releaseKey(); }
        catch (Throwable releaseException) { exception.addSuppressed(releaseException); }
        if (originalLocale != null && client.getLanguageManager() != null
                && !originalLocale.equals(client.getLanguageManager().getSelected())) {
            if (reloadTarget != null && reloadTarget.equals("restore:" + originalLocale)) {
                restoreAfterFailure(client);
                com.mojang.logging.LogUtils.getLogger().error("AE2 item coverage validation failed during " + failedPhase, exception);
                return;
            }
            try {
                client.getLanguageManager().setSelected(originalLocale);
                client.options.languageCode = originalOptionLanguage;
                reloadTarget = "restore:" + originalLocale;
                reload = client.reloadResourcePacks();
                phase = "restoring_locale_after_failure";
                saveProgressQuietly(client);
                com.mojang.logging.LogUtils.getLogger().error("AE2 item coverage validation failed during " + failedPhase, exception);
                return;
            } catch (Throwable restoreException) {
                exception.addSuppressed(restoreException);
                failure += " | could not begin locale restoration: " + restoreException;
            }
        }
        restoreAfterFailure(client);
        com.mojang.logging.LogUtils.getLogger().error("AE2 item coverage validation failed during " + failedPhase, exception);
    }

    private static void restoreAfterFailure(Minecraft client) {
        reload = null;
        try {
            if (client.options != null && originalGuiScale >= 0) {
                client.options.guiScale().set(originalGuiScale);
                client.resizeDisplay();
            }
            if (returnScreen != null) client.setScreen(returnScreen);
        } catch (Throwable ignored) { }
        phase = "failed";
        passed = false;
        finished = true;
        recordProgressEvent("failed", client);
        saveProgressQuietly(client);
    }

    private static boolean failuresAbsent() { return failure == null && overflows.isEmpty(); }

    private static boolean clickCategory(PonderUI ui, PonderTag tag) throws Exception {
        List<PonderTag> tags = ((PonderUIAccessor) (Object) ui).ae2ponder$getTags();
        int index = -1;
        for (int i = 0; i < tags.size(); i++) if (tags.get(i).getId().equals(tag.getId())) index = i;
        if (index < 0) throw new IllegalStateException("Native Ponder W screen has no expected category button: " + tag.getId());
        Field field = PonderUI.class.getDeclaredField("tagButtons");
        field.setAccessible(true);
        @SuppressWarnings("unchecked") List<PonderButton> buttons = (List<PonderButton>) field.get(ui);
        if (buttons.size() != tags.size()) throw new IllegalStateException("Native Ponder category buttons do not match screen tags");
        PonderButton button = buttons.get(index);
        if (!button.isVisible()) return false;
        clickWidget(ui, button, "Native Ponder category " + tag.getId());
        return true;
    }

    private static PonderButton ponderButton(PonderUI ui, String name) throws Exception {
        Field field = PonderUI.class.getDeclaredField(name);
        field.setAccessible(true);
        return (PonderButton) field.get(ui);
    }

    private static void clickWidget(Screen screen, net.minecraft.client.gui.components.AbstractWidget widget, String label) {
        double x = widget.getX() + widget.getWidth() / 2.0;
        double y = widget.getY() + widget.getHeight() / 2.0;
        if (!screen.mouseClicked(x, y, 0)) throw new IllegalStateException(label + " did not handle its native click");
    }

    private static boolean clickPonderBackIfReady(Screen screen) throws ReflectiveOperationException {
        if (!(screen instanceof NavigatableSimiScreen))
            throw new IllegalStateException("Expected a native Ponder navigation screen: " + screen);
        Field field = NavigatableSimiScreen.class.getDeclaredField("backTrack");
        field.setAccessible(true);
        BoxWidget back = (BoxWidget) field.get(screen);
        if (back == null || !back.visible || !back.active) return false;
        clickWidget(screen, back, "Native Ponder back");
        return true;
    }

    private static List<String> sceneIds(PonderUI ui) throws Exception {
        Field scenesField = PonderUI.class.getDeclaredField("scenes");
        scenesField.setAccessible(true);
        @SuppressWarnings("unchecked") List<PonderScene> scenes = (List<PonderScene>) scenesField.get(ui);
        return scenes.stream().map(scene -> scene.getId().toString()).toList();
    }

    private static List<String> registeredGuideSceneIds(ResourceLocation itemId) {
        return PonderIndex.getSceneAccess().compile(itemId).stream().map(PonderScene::getId)
                .map(ResourceLocation::toString).filter(id -> id.startsWith("ae2ponder:"))
                .toList();
    }

    private static List<String> normalizeLessonIds(List<String> rawIds) {
        if (rawIds == null) return List.of();
        return rawIds.stream().map(raw -> {
            ResourceLocation id = raw.contains(":") ? exactId(raw) : GuideResources.of("ae2ponder", raw);
            if (!id.getNamespace().equals("ae2ponder"))
                throw new IllegalStateException("Expected lesson ID is outside the guide namespace: " + raw);
            return id.toString();
        }).toList();
    }

    private static List<String> availableExpectedLessonIds(List<String> rawIds) {
        List<String> normalized = normalizeLessonIds(rawIds);
        Set<String> declaredLessons = GuideBook.allLessons().stream()
                .map(lesson -> GuideResources.of("ae2ponder", lesson.id()).toString())
                .collect(java.util.stream.Collectors.toSet());
        Set<String> loadedLessons = GuideBook.lessons().stream()
                .map(lesson -> GuideResources.of("ae2ponder", lesson.id()).toString())
                .collect(java.util.stream.Collectors.toSet());
        return normalized.stream()
                // Keep unknown IDs so the independent expected-entrance check fails loudly on bad fixture data.
                .filter(id -> !declaredLessons.contains(id) || loadedLessons.contains(id))
                .toList();
    }

    private static boolean scopedTextMode() {
        return STAFF_ONLY && !TEXT_KEYS.isEmpty();
    }

    private static boolean localeChecksEnabled() {
        return !STAFF_ONLY || !TEXT_KEYS.isEmpty();
    }

    private static int expectedLocaleCount() {
        if (!localeChecksEnabled()) return 0;
        return textSelection == null ? 22 : textSelection.locales().size();
    }

    private static int expectedTextKeyCheckCount() {
        if (!localeChecksEnabled()) return 0;
        if (textSelection == null) return effectiveLocaleKeys().size() * expectedLocaleCount();
        return textSelection.locales().values().stream().mapToInt(List::size).sum();
    }

    private static int expectedIndexedCaptionCount() {
        if (!localeChecksEnabled()) return 0;
        if (textSelection == null)
            return (int) effectiveLocaleKeys().stream().filter(GuideCaptionValidation::isCaptionKey).count()
                    * expectedLocaleCount();
        return (int) textSelection.locales().values().stream().flatMap(Collection::stream)
                .filter(GuideCaptionValidation::isCaptionKey).count();
    }

    private static int expectedDescriptionExpansionCount() {
        if (!localeChecksEnabled()) return 0;
        if (textSelection == null)
            return (int) effectiveLocaleKeys().stream().filter(key -> key.startsWith("ae2ponder.description.")).count()
                    * expectedLocaleCount();
        return (int) textSelection.locales().values().stream().flatMap(Collection::stream)
                .filter(key -> key.startsWith("ae2ponder.description.")).count();
    }

    private static int expectedCaptionLocaleCount() {
        if (!localeChecksEnabled()) return 0;
        if (textSelection == null)
            return effectiveLocaleKeys().stream().anyMatch(GuideCaptionValidation::isCaptionKey)
                    ? expectedLocaleCount() : 0;
        return (int) textSelection.locales().values().stream()
                .filter(keys -> keys.stream().anyMatch(GuideCaptionValidation::isCaptionKey)).count();
    }

    private static List<String> localeKeys(String locale) {
        if (textSelection != null) return textSelection.locales().getOrDefault(locale, List.of());
        return effectiveLocaleKeys();
    }

    private static List<String> allSelectedTextKeys() {
        if (textSelection != null)
            return textSelection.locales().values().stream().flatMap(Collection::stream).distinct().sorted().toList();
        if (!TEXT_KEYS.isEmpty()) return TEXT_KEYS;
        return fixture == null || fixture.changedKeys() == null ? List.of() : fixture.changedKeys();
    }

    private static Map<String, List<String>> selectedLocaleTextKeys() {
        if (textSelection == null) return Map.of();
        var selectedKeys = new LinkedHashMap<String, List<String>>();
        textSelection.locales().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> selectedKeys.put(entry.getKey(), List.copyOf(entry.getValue())));
        return Map.copyOf(selectedKeys);
    }

    private static List<String> effectiveLocaleKeys() {
        if (textSelection != null) return allSelectedTextKeys();
        if (!TEXT_KEYS.isEmpty()) return TEXT_KEYS;
        return fixture == null || fixture.changedKeys() == null ? List.of() : fixture.changedKeys();
    }

    private static List<String> readAdditionalItemIds() {
        String raw = System.getProperty("ae2ponder.validationAdditionalItems", "").trim();
        if (raw.isEmpty()) return List.of();
        List<String> ids = Arrays.stream(raw.split(",", -1)).map(String::trim).toList();
        if (ids.stream().anyMatch(String::isEmpty) || ids.stream().distinct().count() != ids.size())
            throw new IllegalArgumentException("Additional validation item IDs contain an empty or duplicate value");
        if (ids.stream().anyMatch(id -> !id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")))
            throw new IllegalArgumentException("Additional validation items must be fully qualified registry IDs: " + raw);
        return List.copyOf(ids);
    }

    private static List<String> readTextKeys() {
        String raw = System.getProperty("ae2ponder.validationTextKeys", "").trim();
        if (raw.isEmpty()) return List.of();
        List<String> keys = Arrays.stream(raw.split(",", -1)).map(String::trim).toList();
        if (keys.stream().anyMatch(String::isEmpty) || keys.stream().distinct().count() != keys.size())
            throw new IllegalArgumentException("Additional validation text keys contain an empty or duplicate value");
        if (keys.stream().anyMatch(key -> !key.matches("[a-z0-9_.-]+")))
            throw new IllegalArgumentException("TextKeys must be translation keys: " + raw);
        return List.copyOf(keys);
    }

    private static ItemStack staffStack(double energy) throws Exception {
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(exactId(STAFF_ID)));
        Method injector = Arrays.stream(stack.getItem().getClass().getMethods())
                .filter(method -> method.getName().equals("injectAEPower") && method.getParameterCount() == 3)
                .findFirst().orElseThrow(() -> new IllegalStateException("Charged staff item has no AE storage injector"));
        Class<?> actionable = injector.getParameterTypes()[2];
        Object modulate = Arrays.stream(actionable.getEnumConstants()).filter(value -> value.toString().equals("MODULATE"))
                .findFirst().orElseThrow(() -> new IllegalStateException("AE actionable enum has no MODULATE value"));
        // AE2 returns the energy that could not be inserted, rather than the accepted amount.
        Object remainder = injector.invoke(stack.getItem(), stack, energy, modulate);
        double actual = staffEnergy(stack);
        if (!(remainder instanceof Number number) || Math.abs(number.doubleValue()) > 0.0001
                || Math.abs(actual - energy) > 0.0001)
            throw new IllegalStateException("Could not construct charged staff with exact stored energy: requested="
                    + energy + ", stored=" + actual + ", remainder=" + remainder);
        return stack;
    }

    private static double staffEnergy(ItemStack stack) throws Exception {
        Method getter = Arrays.stream(stack.getItem().getClass().getMethods())
                .filter(method -> method.getName().equals("getAECurrentPower") && method.getParameterCount() == 1)
                .findFirst().orElseThrow(() -> new IllegalStateException("Charged staff has no stored energy getter"));
        return ((Number) getter.invoke(stack.getItem(), stack)).doubleValue();
    }

    private static void releaseKey() { ValidationPlatform.setPonderKeyDown(false); }

    private static ResourceLocation exactId(String raw) {
        if (raw == null || raw.indexOf(':') <= 0) throw new IllegalStateException("Expected a fully qualified registry ID: " + raw);
        ResourceLocation id = GuideResources.parse(raw);
        if (!id.toString().equals(raw)) throw new IllegalStateException("Registry ID was normalized unexpectedly: " + raw + " -> " + id);
        return id;
    }

    private static String namespace(String id) { return exactId(id).getNamespace(); }

    private static int size(Collection<?> values) { return values == null ? 0 : values.size(); }

    private static int size(Map<?, ?> values) { return values == null ? 0 : values.size(); }

    private static void requireUnique(Collection<String> values, String label) {
        if (values == null || values.stream().distinct().count() != values.size())
            throw new IllegalStateException("Independent item fixture has a missing or duplicate ID in " + label);
    }

    private static List<FormattedText> wrap(Font font, String locale, String text, int width) {
        if (isRtl(locale)) return dev.ae2guide.client.RightToLeftText.wrap(font, text, width, Style.EMPTY);
        return font.getSplitter().splitLines(text, width, Style.EMPTY);
    }

    private static int renderedWidth(Font font, String locale, String text) {
        return isRtl(locale) ? dev.ae2guide.client.RightToLeftText.width(font, text) : font.width(text);
    }

    private static String withoutWhitespace(String text) {
        var result = new StringBuilder();
        text.codePoints().filter(codePoint -> !Character.isWhitespace(codePoint))
                .forEach(result::appendCodePoint);
        return result.toString();
    }

    private static boolean isRtl(String locale) {
        return switch (locale.split("_", 2)[0]) {
            case "ar", "fa", "he", "ur" -> true;
            default -> false;
        };
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        var result = new StringBuilder(digest.length * 2);
        for (byte value : digest) result.append(String.format(Locale.ROOT, "%02x", value));
        return result.toString();
    }

    private record Fixture(String sourceHash, List<String> changedKeys, Map<String, PlatformFixture> platforms) {}
    private record LocaleTextSelection(String platform, Map<String, List<String>> locales) {}
    private record PlatformFixture(List<String> playerItems, List<String> addonItems, List<String> excludedItems,
                                   Map<String, CableFamily> cableFamilies,
                                   Map<String, ExpectedEntrance> expectedNewEntrances,
                                   Map<String, Object> excludedReasons) {}
    private record CableFamily(List<String> items, String firstLesson, String chapter) {}
    private record ExpectedEntrance(List<String> ponder, String description) {}
    private record WTarget(String itemId, ItemStack stack, List<String> expectedScenes, String family,
                           String chapter, boolean categoryRepresentative, Double energy) {
        String familyChapter() { return chapter; }
    }
    private record RuntimeItemNameAudit(Map<String, String> names, List<String> unavailable) {}

    /** Renders one real expanded Shift description at the current locale for screenshot verification. */
    private static final class CoverageTooltipScreen extends Screen {
        private final String locale;
        private final ItemStack stack;
        private boolean rendered;

        private CoverageTooltipScreen(String locale, ItemStack stack) {
            super(Component.empty());
            this.locale = locale;
            this.stack = stack.copy();
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xff181c24);
            graphics.drawCenteredString(font, Component.literal("Item tooltip render · " + locale),
                    width / 2, 20, 0xfff0f0f0);
            int iconX = width / 2 - 8;
            int iconY = height / 3;
            graphics.renderItem(stack, iconX, iconY);
            graphics.renderItemDecorations(font, stack, iconX, iconY);
            var tooltip = new ArrayList<Component>();
            tooltip.add(stack.getHoverName());
            GuideItemDescriptions.append(stack, tooltip, true);
            int tooltipX = Math.max(8, (width - 240) / 2);
            int tooltipY = Math.min(height - 48, height / 2);
            graphics.renderComponentTooltip(font, tooltip, tooltipX, tooltipY);
            rendered = true;
        }

        @Override
        public boolean isPauseScreen() { return false; }
    }
}
