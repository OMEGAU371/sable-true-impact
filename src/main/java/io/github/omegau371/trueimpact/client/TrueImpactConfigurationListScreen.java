package io.github.omegau371.trueimpact.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * Reskins list-value rows (used by "compactionRules" and any future list config): the
 * built-in ConfigurationListScreen's per-row up/down/delete buttons leave a permanent blank
 * gap where the up button would be on the first row (and where the down button would be on
 * the last row) -- the label always starts after both button slots regardless of whether
 * they're actually shown, so edge rows look lopsided next to middle rows that have both
 * buttons. This grows the remaining direction button to fill that gap instead, and for a
 * single-element list (neither button applies) gives the freed space to the label.
 *
 * createListLabel() does the button-growth reskin above. addFooter()/createCancelButton()
 * mirror TrueImpactConfigurationSectionScreen's Cancel button (this class is a sibling of
 * that one, not a subclass of it, so it can't just inherit the override) so a list-editing
 * screen like compactionRules discards its edits the same way any other screen does, instead
 * of falling back to the built-in single-step Undo button.
 */
public class TrueImpactConfigurationListScreen<T> extends ConfigurationScreen.ConfigurationListScreen<T> {

    private Button cancelButton;

    public TrueImpactConfigurationListScreen(Context context, String key, Component title,
            ModConfigSpec.ListValueSpec spec, ModConfigSpec.ConfigValue<List<T>> valueList) {
        super(context, key, title, spec, valueList);
    }

    /**
     * Widened to public (from protected) so TrueImpactConfigurationSectionScreen.createList()
     * -- a sibling class, not this class or a subtype of it -- can call it on freshly built
     * instances; Java's protected-access rule only allows that from the same or a subclass.
     */
    @Override
    public TrueImpactConfigurationListScreen<T> rebuild() {
        super.rebuild();
        return this;
    }

    @Override
    protected AbstractWidget createListLabel(int idx) {
        return new GrowingListLabelWidget(0, 0, Button.DEFAULT_WIDTH, Button.DEFAULT_HEIGHT,
                Component.translatable("neoforge.configuration.uitext.listelement", idx), idx);
    }

    public class GrowingListLabelWidget extends AbstractContainerWidget {
        protected final Button upButton = Button.builder(ConfigurationScreen.MOVE_LIST_ELEMENT_UP, this::up).build();
        protected final Button downButton = Button.builder(ConfigurationScreen.MOVE_LIST_ELEMENT_DOWN, this::down).build();
        protected final Button delButton = Button.builder(ConfigurationScreen.REMOVE_LIST_ELEMENT, this::rem).build();
        protected final StringWidget label = new StringWidget(0, 0, 0, 0, Component.empty(), font).alignLeft();
        protected final int idx;
        protected final boolean isFirst;
        protected final boolean isLast;

        public GrowingListLabelWidget(int x, int y, int width, int height, Component labelText, int idx) {
            super(x, y, width, height, labelText);
            this.idx = idx;
            this.isFirst = idx == 0;
            this.isLast = idx + 1 == cfgList.size();
            label.setMessage(labelText);
            checkButtons();
            updateLayout();
        }

        @Override
        public void setX(int pX) { super.setX(pX); updateLayout(); }

        @Override
        public void setY(int pY) { super.setY(pY); updateLayout(); }

        @Override
        public void setHeight(int pHeight) { super.setHeight(pHeight); updateLayout(); }

        @Override
        public void setWidth(int pWidth) { super.setWidth(pWidth); updateLayout(); }

        @Override
        public void setSize(int pWidth, int pHeight) { super.setSize(pWidth, pHeight); updateLayout(); }

        protected void updateLayout() {
            int h = getHeight();
            int doubleSlot = 2 * h + 2;

            if (isFirst && isLast) {
                // Single-element list: neither up nor down applies -- give the label the space.
                label.setX(getX());
                label.setWidth(getWidth() - h - 2);
            } else if (isFirst) {
                // No up button -- down button grows to occupy both slots.
                downButton.setX(getX());
                downButton.setWidth(doubleSlot);
                label.setX(getX() + doubleSlot + 2);
                label.setWidth(getWidth() - doubleSlot - 2 - (h + 2));
            } else if (isLast) {
                // No down button -- up button grows to occupy both slots.
                upButton.setX(getX());
                upButton.setWidth(doubleSlot);
                label.setX(getX() + doubleSlot + 2);
                label.setWidth(getWidth() - doubleSlot - 2 - (h + 2));
            } else {
                upButton.setX(getX());
                upButton.setWidth(h);
                downButton.setX(getX() + h + 2);
                downButton.setWidth(h);
                label.setX(getX() + 2 * 22);
                label.setWidth(getWidth() - 3 * (h + 2));
            }
            delButton.setX(getX() + getWidth() - h);
            delButton.setWidth(h);

            upButton.setY(getY());
            downButton.setY(getY());
            delButton.setY(getY());
            label.setY(getY());

            upButton.setHeight(h);
            downButton.setHeight(h);
            delButton.setHeight(h);
            label.setHeight(h);
        }

        void up(Button button) { swap(idx - 1, false); }
        void down(Button button) { swap(idx, false); }
        void rem(Button button) { del(idx, false); }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(upButton, label, downButton, delButton);
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            checkButtons();
            label.render(guiGraphics, mouseX, mouseY, partialTick);
            if (!isFirst) {
                upButton.render(guiGraphics, mouseX, mouseY, partialTick);
            }
            if (!isLast) {
                downButton.render(guiGraphics, mouseX, mouseY, partialTick);
            }
            delButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        protected void checkButtons() {
            upButton.visible = !isFirst;
            upButton.active = !isFirst && swap(idx - 1, true);
            downButton.visible = !isLast;
            downButton.active = !isLast && swap(idx, true);
            ModConfigSpec.Range<Integer> sizeRange = spec.getSizeRange();
            delButton.active = !cfgList.isEmpty() && (sizeRange == null || sizeRange.test(cfgList.size() - 1)) && del(idx, true);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            // Matches the built-in ListLabelWidget: no narration provided upstream either.
        }
    }

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
