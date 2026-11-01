/*
 * Portions adapted from Ponder's PonderTagScreen.
 * Copyright (c) 2022 The Create Team. Licensed under the MIT License.
 * Source: https://github.com/Creators-of-Create/Ponder/blob/mc1.21.1/dev/common/src/main/java/net/createmod/ponder/foundation/ui/PonderTagScreen.java
 * Changes: responsive layout and pagination for the AE2 related-item screen.
 * See LICENSES/MIT-Ponder.txt and THIRD_PARTY_NOTICES.md.
 */
package dev.ae2guide.mixin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.catnip.gui.UIRenderHelper;
import net.createmod.catnip.gui.element.BoxElement;
import net.createmod.ponder.Ponder;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderTag;
import net.createmod.ponder.foundation.ui.AbstractPonderScreen;
import net.createmod.ponder.foundation.ui.PonderButton;
import net.createmod.ponder.foundation.ui.PonderTagScreen;
import net.createmod.ponder.foundation.ui.PonderUI;
import dev.ae2guide.GuideResources;
import dev.ae2guide.client.GuideNavigation;
import dev.ae2guide.client.GuidePonderScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

@Mixin(value = PonderTagScreen.class, remap = false)
public abstract class PonderTagScreenMixin extends AbstractPonderScreen {

	private static final int AE2PONDER_ITEM_SIZE = 20;
	private static final int AE2PONDER_CELL_PITCH = 36;
	private static final int AE2PONDER_MAX_COLUMNS = 12;
	private static final int AE2PONDER_PAGE_HEIGHT = 20;
	private static final int AE2PONDER_PAGE_BOTTOM_GAP = 60;

	@Shadow
	public abstract PonderTag getTag();

	@Unique
	private List<AbstractWidget> ae2ponder$pageWidgets;
	@Unique
	private List<ResourceLocation> ae2ponder$itemIds;
	@Unique
	private Map<ResourceLocation, ItemStack> ae2ponder$itemStacks;
	@Unique
	private int ae2ponder$page;
	@Unique
	private int ae2ponder$pageCount;
	@Unique
	private int ae2ponder$columns;
	@Unique
	private int ae2ponder$rows;
	@Unique
	private int ae2ponder$gridX;
	@Unique
	private int ae2ponder$gridY;
	@Unique
	private int ae2ponder$relatedY;
	@Unique
	private int ae2ponder$descriptionY;
	@Unique
	private int ae2ponder$descriptionHeight;
	@Unique
	private List<FormattedCharSequence> ae2ponder$descriptionLines;

	@Inject(method = "init", at = @At(value = "HEAD", remap = false), cancellable = true, remap = true)
	private void ae2ponder$initResponsivePages(CallbackInfo ci) {
		if (!ae2ponder$isGuideTag())
			return;

		// Keep Ponder's normal history/back-track setup, but replace its fixed item layout.
		super.init();
		if (ae2ponder$pageWidgets == null)
			ae2ponder$pageWidgets = new ArrayList<>();
		ae2ponder$itemStacks = ae2ponder$collectItems(getTag());
		ae2ponder$itemIds = List.copyOf(ae2ponder$itemStacks.keySet());
		ae2ponder$layout();
		ae2ponder$rebuildPageWidgets();
		ci.cancel();
	}

	@Inject(method = "renderWindow", at = @At("HEAD"), cancellable = true, remap = false)
	private void ae2ponder$renderResponsivePages(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks,
			CallbackInfo ci) {
		if (!ae2ponder$isGuideTag())
			return;

		// Preserve Ponder's animated back-track arrow and hover label.
		super.renderWindow(graphics, mouseX, mouseY, partialTicks);
		ae2ponder$renderHeader(graphics);
		ae2ponder$renderRelatedHeading(graphics);
		ae2ponder$renderPageNumber(graphics);
		ci.cancel();
	}

	@Unique
	private boolean ae2ponder$isGuideTag() {
		return getTag() != null && "ae2ponder".equals(getTag().getId().getNamespace());
	}

	@Unique
	private Map<ResourceLocation, ItemStack> ae2ponder$collectItems(PonderTag tag) {
		Map<ResourceLocation, ItemStack> byId = new LinkedHashMap<>();
		String chapterId = tag.getId().getPath();
		ItemStack mainItem = tag.getMainItem();
		if (!mainItem.isEmpty()
				&& GuideNavigation.firstLessonForItem(BuiltInRegistries.ITEM.getKey(mainItem.getItem()), chapterId) != null) {
			ResourceLocation mainId = BuiltInRegistries.ITEM.getKey(mainItem.getItem());
			byId.put(mainId, mainItem.copy());
		} else {
			var fallbackLesson = GuideNavigation.firstLessonForChapter(chapterId);
			if (fallbackLesson != null) {
				ResourceLocation fallbackId = GuideResources.parse(fallbackLesson.icon());
				var fallbackItem = BuiltInRegistries.ITEM.getOptional(fallbackId).orElse(null);
				if (fallbackItem != null) byId.put(fallbackId, new ItemStack(fallbackItem));
			}
		}

		PonderIndex.getTagAccess().getItems(tag).stream()
			.forEach(id -> {
				if (byId.containsKey(id) || GuideNavigation.firstLessonForItem(id, chapterId) == null)
					return;
				ItemLike item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
				if (item == null) item = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
				if (item != null) byId.put(id, new ItemStack(item));
			});

		return byId;
	}

	@Unique
	private void ae2ponder$layout() {
		int availableWidth = Math.max(AE2PONDER_ITEM_SIZE, width - 32);
		ae2ponder$columns = Math.max(1, Math.min(AE2PONDER_MAX_COLUMNS,
			(availableWidth + (AE2PONDER_CELL_PITCH - AE2PONDER_ITEM_SIZE)) / AE2PONDER_CELL_PITCH));

		int titleWidth = Math.max(1, width - 74);
		List<FormattedCharSequence> titleLines = font.split(Component.literal(getTag().getTitle()), titleWidth);
		int titleHeight = Math.max(font.lineHeight, Math.min(2, titleLines.size()) * font.lineHeight);
		int headerBottom = Math.max(48, 26 + titleHeight);

		ae2ponder$descriptionY = headerBottom + 4;
		int descriptionWidth = Math.max(1, width - 32);
		List<FormattedCharSequence> allDescriptionLines = font.split(Component.literal(getTag().getDescription()), descriptionWidth);
		int pageY = height - AE2PONDER_PAGE_BOTTOM_GAP - AE2PONDER_PAGE_HEIGHT;
		int maxDescriptionHeight = Math.max(0, pageY - 8 - AE2PONDER_ITEM_SIZE - (ae2ponder$descriptionY + 8 + 15));
		int visibleDescriptionLines = Math.min(allDescriptionLines.size(), maxDescriptionHeight / font.lineHeight);
		ae2ponder$descriptionLines = allDescriptionLines.subList(0, visibleDescriptionLines);
		ae2ponder$descriptionHeight = visibleDescriptionLines * font.lineHeight;

		ae2ponder$relatedY = ae2ponder$descriptionY + ae2ponder$descriptionHeight + 8;
		ae2ponder$gridY = ae2ponder$relatedY + 15;

		int gridWidth = ae2ponder$columns * AE2PONDER_CELL_PITCH - (AE2PONDER_CELL_PITCH - AE2PONDER_ITEM_SIZE);
		ae2ponder$gridX = Math.max(0, (width - gridWidth) / 2);

		int availableGridHeight = Math.max(AE2PONDER_ITEM_SIZE, pageY - 8 - ae2ponder$gridY);
		ae2ponder$rows = Math.max(1, 1 + (availableGridHeight - AE2PONDER_ITEM_SIZE) / AE2PONDER_CELL_PITCH);
		int perPage = Math.max(1, ae2ponder$columns * ae2ponder$rows);
		ae2ponder$pageCount = Math.max(1, (ae2ponder$itemIds.size() + perPage - 1) / perPage);
		ae2ponder$page = Math.max(0, Math.min(ae2ponder$page, ae2ponder$pageCount - 1));
	}

	@Unique
	private void ae2ponder$rebuildPageWidgets() {
		for (AbstractWidget widget : ae2ponder$pageWidgets)
			removeWidget(widget);
		ae2ponder$pageWidgets.clear();

		int perPage = Math.max(1, ae2ponder$columns * ae2ponder$rows);
		int start = ae2ponder$page * perPage;
		int end = Math.min(ae2ponder$itemIds.size(), start + perPage);
		for (int index = start; index < end; index++) {
			ResourceLocation id = ae2ponder$itemIds.get(index);
			ItemStack stack = ae2ponder$itemStacks.get(id);
			int localIndex = index - start;
			int column = localIndex % ae2ponder$columns;
			int row = localIndex / ae2ponder$columns;
			int x = ae2ponder$gridX + column * AE2PONDER_CELL_PITCH;
			int y = ae2ponder$gridY + row * AE2PONDER_CELL_PITCH;
			PonderButton itemButton = new PonderButton(x, y).showing(stack.copy());
			if (PonderIndex.getSceneAccess().doScenesExistForId(id)) {
				itemButton.withCallback((mouseX, mouseY) -> {
					centerScalingOn(mouseX, mouseY);
					var lesson = GuideNavigation.firstLessonForItem(id, getTag().getId().getPath());
					if (lesson != null)
						GuidePonderScreen.open(lesson);
					else
						ScreenOpener.transitionTo(PonderUI.of(stack.copy(), getTag()));
				});
			} else {
				itemButton.withBorderColors(id.getNamespace().equals("minecraft")
					? PonderUI.MISSING_VANILLA_ENTRY
					: PonderUI.MISSING_MODDED_ENTRY).animateColors(false);
			}
			ae2ponder$addPageWidget(itemButton);
		}

		int pageY = height - AE2PONDER_PAGE_BOTTOM_GAP - AE2PONDER_PAGE_HEIGHT;
		int controlsWidth = 96;
		int controlsX = Math.max(0, (width - controlsWidth) / 2);
		Button previous = Button.builder(Component.literal("<"), button -> ae2ponder$changePage(-1))
			.bounds(controlsX, pageY, 28, AE2PONDER_PAGE_HEIGHT).build();
		previous.active = ae2ponder$page > 0;
		ae2ponder$addPageWidget(previous);

		Button next = Button.builder(Component.literal(">"), button -> ae2ponder$changePage(1))
			.bounds(controlsX + controlsWidth - 28, pageY, 28, AE2PONDER_PAGE_HEIGHT).build();
		next.active = ae2ponder$page + 1 < ae2ponder$pageCount;
		ae2ponder$addPageWidget(next);
	}

	@Unique
	private void ae2ponder$addPageWidget(AbstractWidget widget) {
		ae2ponder$pageWidgets.add(widget);
		addRenderableWidget(widget);
	}

	@Unique
	private void ae2ponder$changePage(int delta) {
		int newPage = Math.max(0, Math.min(ae2ponder$page + delta, ae2ponder$pageCount - 1));
		if (newPage == ae2ponder$page)
			return;
		ae2ponder$page = newPage;
		ae2ponder$rebuildPageWidgets();
	}

	@Unique
	private void ae2ponder$renderHeader(GuiGraphics graphics) {
		PonderTag tag = getTag();
		new BoxElement().withBackground(PonderUI.BACKGROUND_FLAT).gradientBorder(PonderUI.COLOR_IDLE)
			.at(14, 10, 100).withBounds(Math.max(24, width - 28), 36).render(graphics);
		new BoxElement().withBackground(PonderUI.BACKGROUND_FLAT).gradientBorder(PonderUI.COLOR_IDLE)
			.at(16, 12, 110).withBounds(32, 32).render(graphics);
		graphics.pose().pushPose();
		graphics.pose().translate(20, 16, 120);
		graphics.pose().scale(1.5f, 1.5f, 1.5f);
		tag.render(graphics, 0, 0);
		graphics.pose().popPose();
		if (ae2ponder$descriptionHeight > 0) {
			new BoxElement().withBackground(PonderUI.BACKGROUND_FLAT).gradientBorder(PonderUI.COLOR_IDLE)
				.at(14, ae2ponder$descriptionY - 3, 90).withBounds(Math.max(24, width - 28), ae2ponder$descriptionHeight + 6)
				.render(graphics);
		}

		graphics.pose().pushPose();
		graphics.pose().translate(0, 0, 200);
		graphics.drawString(font, Ponder.lang().translate("ui.pondering_tag").component(),
			58, 12, UIRenderHelper.COLOR_TEXT_DARKER.getFirst().getRGB(), false);
		List<FormattedCharSequence> titleLines = font.split(Component.literal(tag.getTitle()), Math.max(1, width - 74));
		int lineCount = Math.min(2, titleLines.size());
		for (int i = 0; i < lineCount; i++)
			graphics.drawString(font, titleLines.get(i), 58, 25 + i * font.lineHeight,
				UIRenderHelper.COLOR_TEXT.getFirst().getRGB(), false);

		if (ae2ponder$descriptionHeight > 0) {
			for (int i = 0; i < ae2ponder$descriptionLines.size(); i++)
				graphics.drawString(font, ae2ponder$descriptionLines.get(i), 20,
					ae2ponder$descriptionY + i * font.lineHeight, UIRenderHelper.COLOR_TEXT.getFirst().getRGB(), false);
		}
		graphics.pose().popPose();
	}

	@Unique
	private void ae2ponder$renderRelatedHeading(GuiGraphics graphics) {
		String relatedTitle = Ponder.lang().translate(AbstractPonderScreen.ASSOCIATED).string();
		int titleWidth = font.width(relatedTitle);
		int boxWidth = Math.min(width - 28, titleWidth + 12);
		int boxX = Math.max(14, (width - boxWidth) / 2);
		new BoxElement().withBackground(PonderUI.BACKGROUND_FLAT).gradientBorder(PonderUI.COLOR_IDLE)
			.at(boxX, ae2ponder$relatedY - 2, 100).withBounds(boxWidth, 13).render(graphics);
		graphics.pose().pushPose();
		graphics.pose().translate(0, 0, 200);
		graphics.drawCenteredString(font, relatedTitle, width / 2, ae2ponder$relatedY,
			UIRenderHelper.COLOR_TEXT.getFirst().getRGB());
		graphics.pose().popPose();
	}

	@Unique
	private void ae2ponder$renderPageNumber(GuiGraphics graphics) {
		String pageLabel = ae2ponder$itemIds.isEmpty() ? "0/0" : (ae2ponder$page + 1) + "/" + ae2ponder$pageCount;
		graphics.drawCenteredString(font, pageLabel, width / 2, height - AE2PONDER_PAGE_BOTTOM_GAP - 14,
			UIRenderHelper.COLOR_TEXT.getFirst().getRGB());
	}
}
