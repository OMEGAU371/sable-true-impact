package io.github.omegau371.trueimpact.client;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Reskins category navigation rows: NeoForge's own ConfigurationSectionScreen always renders
 * a category as [label text][small ">>" button] side by side (see rebuild()'s addSmall calls
 * in the vanilla source). This instead puts the category name directly on a single
 * full-width button -- matching how Sable's own settings screen looks (built with vanilla
 * OptionsList.addBig), per user request.
 *
 * createSection()/rebuild() reskin the category-navigation layout; addFooter() replaces the
 * single-step Undo button with a real Cancel (discard all edits made on this screen + go
 * back without saving). onClose()/restart-confirmation and the server-vs-client edit gating
 * are untouched, inherited as-is from the built-in screen.
 */
public class TrueImpactConfigurationSectionScreen extends ConfigurationScreen.ConfigurationSectionScreen {

    private Button cancelButton;

    public TrueImpactConfigurationSectionScreen(Screen parent, ModConfig.Type type, ModConfig modConfig, Component title) {
        super(parent, type, modConfig, title);
    }

    public TrueImpactConfigurationSectionScreen(Context parentContext, Screen parent, Map<String, Object> valueSpecs,
            String key, Set<? extends UnmodifiableConfig.Entry> entrySet, Component title) {
        super(parentContext, parent, valueSpecs, key, entrySet, title);
    }

    @Override
    protected Element createSection(String key, UnmodifiableConfig subconfig, UnmodifiableConfig subsection) {
        if (subconfig.isEmpty()) return null;
        Component name = getTranslationComponent(key);
        Button button = Button.builder(name,
                        btn -> minecraft.setScreen(sectionCache.computeIfAbsent(key,
                                k -> new TrueImpactConfigurationSectionScreen(context, this, subconfig.valueMap(), key,
                                        subsection.entrySet(), name).rebuild())))
                .tooltip(Tooltip.create(getTooltipComponent(key, null)))
                .width(Button.DEFAULT_WIDTH)
                .build();
        return new Element(null, getTooltipComponent(key, null), button, false);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    protected TrueImpactConfigurationSectionScreen rebuild() {
        if (list == null) return this; // may be called early, wait for init() then

        list.children().clear();
        boolean hasUndoableElements = false;

        final List<Element> elements = new ArrayList<>();
        for (final UnmodifiableConfig.Entry entry : context.entries()) {
            final String key = entry.getKey();
            final Object rawValue = entry.getRawValue();
            switch (rawValue) {
                case ModConfigSpec.ConfigValue cv -> {
                    var valueSpec = getValueSpec(key);
                    var element = switch (valueSpec) {
                        case ModConfigSpec.ListValueSpec listValueSpec -> createList(key, listValueSpec, cv);
                        case ModConfigSpec.ValueSpec spec when cv.getClass() == ModConfigSpec.ConfigValue.class && spec.getDefault() instanceof String ->
                                createStringValue(key, valueSpec::test, () -> (String) cv.getRaw(), (Consumer<String>) cv::set);
                        case ModConfigSpec.ValueSpec spec when cv.getClass() == ModConfigSpec.ConfigValue.class && spec.getDefault() instanceof Integer ->
                                createIntegerValue(key, valueSpec, (Supplier<Integer>) () -> (Integer) cv.getRaw(), (Consumer<Integer>) cv::set);
                        case ModConfigSpec.ValueSpec spec when cv.getClass() == ModConfigSpec.ConfigValue.class && spec.getDefault() instanceof Long ->
                                createLongValue(key, valueSpec, (Supplier<Long>) () -> (Long) cv.getRaw(), (Consumer<Long>) cv::set);
                        case ModConfigSpec.ValueSpec spec when cv.getClass() == ModConfigSpec.ConfigValue.class && spec.getDefault() instanceof Double ->
                                createDoubleValue(key, valueSpec, (Supplier<Double>) () -> (Double) cv.getRaw(), (Consumer<Double>) cv::set);
                        case ModConfigSpec.ValueSpec spec when cv.getClass() == ModConfigSpec.ConfigValue.class && spec.getDefault() instanceof Enum<?> ->
                                createEnumValue(key, valueSpec, (Supplier) cv::getRaw, (Consumer) cv::set);
                        case null -> null;
                        default -> switch (cv) {
                            case ModConfigSpec.BooleanValue value -> createBooleanValue(key, valueSpec, value::getRaw, value::set);
                            case ModConfigSpec.IntValue value -> createIntegerValue(key, valueSpec, value::getRaw, value::set);
                            case ModConfigSpec.LongValue value -> createLongValue(key, valueSpec, value::getRaw, value::set);
                            case ModConfigSpec.DoubleValue value -> createDoubleValue(key, valueSpec, value::getRaw, value::set);
                            case ModConfigSpec.EnumValue value -> createEnumValue(key, valueSpec, (Supplier) value::getRaw, (Consumer) value::set);
                            default -> createOtherValue(key, cv);
                        };
                    };
                    elements.add(context.filter().filterEntry(context, key, element));
                }
                case UnmodifiableConfig subsection when context.valueSpecs().get(key) instanceof UnmodifiableConfig subconfig ->
                        elements.add(createSection(key, subconfig, subsection));
                default -> elements.add(context.filter().filterEntry(context, key, createOtherSection(key, rawValue)));
            }
        }
        elements.addAll(createSyntheticValues());

        // Section/navigation rows (see createSection() above -- name() == null, text lives on
        // the button itself). The root screen (just "general"/"advanced") always goes one per
        // row full-width -- pairing two dissimilar top-level entries side by side looked
        // arbitrary. Nested category screens (e.g. "advanced"'s 8 peer subsections) pair two
        // per row instead; an odd one left over at the end widens to full row width rather
        // than sitting alone at half width with blank space next to it.
        boolean rootScreen = context.keylist().isEmpty();
        AbstractWidget pendingSection = null;
        for (final Element element : elements) {
            if (element == null) continue;
            if (element.name() == null) {
                AbstractWidget widget = element.getWidget(options);
                if (rootScreen) {
                    widget.setWidth(ConfigurationScreen.BIG_BUTTON_WIDTH);
                    list.addSmall(widget, null);
                } else if (pendingSection == null) {
                    pendingSection = widget;
                } else {
                    list.addSmall(pendingSection, widget);
                    pendingSection = null;
                }
            } else {
                if (pendingSection != null) {
                    pendingSection.setWidth(ConfigurationScreen.BIG_BUTTON_WIDTH);
                    list.addSmall(pendingSection, null);
                    pendingSection = null;
                }
                final StringWidget label = new StringWidget(Button.DEFAULT_WIDTH, Button.DEFAULT_HEIGHT, element.name(), font).alignLeft();
                label.setTooltip(Tooltip.create(element.tooltip()));
                list.addSmall(label, element.getWidget(options));
            }
            hasUndoableElements |= element.undoable();
        }
        if (pendingSection != null) {
            pendingSection.setWidth(ConfigurationScreen.BIG_BUTTON_WIDTH);
            list.addSmall(pendingSection, null);
        }

        // Undo is intentionally not created here: the new Cancel button (below) subsumes
        // it -- undoing everything on this screen and leaving -- so a separate single-step
        // Undo button next to it was redundant and confusing.
        if (hasUndoableElements && resetButton == null) {
            createResetButton();
        }
        return this;
    }

    /**
     * Routes list-value rows (e.g. compactionRules) through TrueImpactConfigurationListScreen
     * instead of the vanilla ConfigurationListScreen, so per-row up/down/delete buttons on the
     * list-editing screen don't leave a lopsided blank gap on the first/last row.
     */
    @Override
    protected <E> Element createList(String key, ModConfigSpec.ListValueSpec spec, ModConfigSpec.ConfigValue<List<E>> valueList) {
        Component name = getTranslationComponent(key);
        Button button = Button.builder(name,
                        btn -> minecraft.setScreen(sectionCache.computeIfAbsent(key,
                                k -> new TrueImpactConfigurationListScreen<>(Context.list(context, this), key,
                                        Component.translatable("neoforge.configuration.uitext.breadcrumb.order",
                                                this.getTitle(), ConfigurationScreen.CRUMB_SEPARATOR, name),
                                        spec, valueList).rebuild())))
                .tooltip(Tooltip.create(getTooltipComponent(key, null)))
                .width(Button.DEFAULT_WIDTH)
                .build();
        return new Element(name, getTooltipComponent(key, null), button, false);
    }

    /**
     * Adds a real "Cancel" button alongside undo/reset/done: discards every edit made on
     * THIS screen (walks its own undo stack back to empty) and returns to the previous
     * screen without marking this screen "changed" -- so it won't bubble up and trigger a
     * save. Scoped to this screen only: edits already committed and bubbled up from a child
     * screen that was opened and closed earlier in this session are a separate undo stack
     * and are not touched (each ConfigurationSectionScreen instance owns its own undoManager).
     */
    private void createCancelButton() {
        cancelButton = Button.builder(Component.translatable("true_impact.cancelButton"), button -> {
                    while (undoManager.canUndo()) {
                        undoManager.undo();
                    }
                    changed = false;
                    needsRestart = ModConfigSpec.RestartType.NONE;
                    onClose();
                })
                .tooltip(Tooltip.create(Component.translatable("true_impact.cancelButton.tooltip")))
                .width(Button.SMALL_WIDTH)
                .build();
    }

    @Override
    protected void addFooter() {
        if (resetButton != null) {
            if (cancelButton == null) createCancelButton();
            LinearLayout linearlayout = layout.addToFooter(LinearLayout.horizontal().spacing(8));
            linearlayout.addChild(resetButton);
            linearlayout.addChild(cancelButton);
            linearlayout.addChild(doneButton);
        } else {
            super.addFooter();
        }
    }
}
