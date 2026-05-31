package com.zerodegress.tmct.client.gui;

import com.zerodegress.tmct.client.TmctClientRecipeService;
import com.zerodegress.tmct.jei.JeiRecipeScanner.IngredientData;
import com.zerodegress.tmct.jei.JeiRecipeScanner.RecipeData;
import com.zerodegress.tmct.tree.SimpleTreeCalculator;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;

public final class TreeScreen extends Screen {
    private static final Identifier SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot");

    private static final int TEXT_COLOR = 0xF0F0F0;
    private static final int MUTED_COLOR = 0xA8A8A8;
    private static final int ACCENT_COLOR = 0xF6D365;
    private static final int LINE_COLOR = 0xFF8F8F8F;
    private static final int PANEL_FILL = 0xC0101010;
    private static final int PANEL_BORDER = 0xFF5A5A5A;
    private static final int LABEL_FILL = 0xC0202020;
    private static final int COUNT_FILL = 0xE0101010;
    private static final int COUNT_BORDER = 0xFFF0F0F0;
    private static final int COUNT_TEXT_COLOR = 0xFFFFFFFF;
    private static final int TOGGLE_FILL = 0xE0101010;
    private static final int TOGGLE_BORDER = 0xFFF0F0F0;
    private static final int TOGGLE_SIZE = 10;
    private static final int TOGGLE_TEXT_COLOR = 0xFFFFFFFF;

    private static final int PANEL_X = 12;
    private static final int PANEL_TOP = 52;
    private static final int PANEL_BOTTOM = 34;
    private static final int PANEL_INSET = 10;

    private static final int CONTENT_PADDING_X = 28;
    private static final int CONTENT_PADDING_TOP = 16;
    private static final int CONTENT_PADDING_BOTTOM = 24;

    private static final int ITEM_SLOT_SIZE = 18;
    private static final int ITEM_TEXT_Y = 22;
    private static final int NODE_BOX_HALF_WIDTH = 26;
    private static final int NODE_BOX_HEIGHT = 34;
    private static final int RECIPE_BADGE_GAP = 8;
    private static final int RECIPE_BADGE_SIZE = 18;
    private static final int RECIPE_TEXT_HEIGHT = 10;
    private static final int RECIPE_SIDE_GAP = 10;
    private static final int LAYER_HEIGHT = 96;
    private static final int MIN_SUBTREE_WIDTH = 56;
    private static final int SIBLING_GAP = 12;
    private static final int PAN_STEP = 24;
    private static final float MIN_ZOOM = 0.6F;
    private static final float MAX_ZOOM = 2.25F;
    private static final float ZOOM_STEP = 0.12F;

    private final @Nullable Screen parent;
    private final IngredientData target;
    private final String recipeLibrary;
    private final int maxDepth;
    private final boolean includeHidden;
    private final SimpleTreeCalculator.SimpleTreeResult result;
    private final Map<String, RecipeInfo> recipeInfoCache = new HashMap<>();
    private final Map<String, List<IngredientData>> stationsByRecipeType = new HashMap<>();
    private final Set<String> collapsedPaths = new LinkedHashSet<>();

    private @Nullable LayoutNode layoutRoot;
    private int treeWidth;
    private int treeHeight;
    private int panX;
    private int panY;
    private boolean dragging;
    private float zoom = 1.0F;

    public TreeScreen(
        @Nullable Screen parent,
        IngredientData target,
        String recipeLibrary,
        int maxDepth,
        boolean includeHidden,
        SimpleTreeCalculator.SimpleTreeResult result
    ) {
        super(Component.translatable("screen.tmct.tree.title"));
        this.parent = parent;
        this.target = target;
        this.recipeLibrary = recipeLibrary;
        this.maxDepth = maxDepth;
        this.includeHidden = includeHidden;
        this.result = result;
    }

    @Override
    protected void init() {
        this.rebuildLayout();
        this.clampPan();

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
            .bounds(this.width / 2 - 50, this.height - 24, 100, 20)
            .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.centeredText(this.font, this.title, this.width / 2, 12, TEXT_COLOR);
        graphics.text(this.font, this.headerTarget(), 12, 28, TEXT_COLOR, false);
        graphics.text(
            this.font,
            Component.translatable("screen.tmct.tree.active_library", this.recipeLibrary),
            12,
            40,
            MUTED_COLOR,
            false
        );

        int panelX = PANEL_X;
        int panelY = PANEL_TOP;
        int panelWidth = this.width - PANEL_X * 2;
        int panelHeight = this.height - PANEL_TOP - PANEL_BOTTOM;
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, PANEL_FILL);
        graphics.outline(panelX, panelY, panelWidth, panelHeight, PANEL_BORDER);

        int viewX = panelX + PANEL_INSET;
        int viewY = panelY + PANEL_INSET;
        int viewWidth = panelWidth - PANEL_INSET * 2;
        int viewHeight = panelHeight - PANEL_INSET * 2;

        if (this.layoutRoot == null) {
            graphics.centeredText(
                this.font,
                Component.translatable("screen.tmct.tree.no_recipe"),
                panelX + panelWidth / 2,
                panelY + panelHeight / 2 - 4,
                MUTED_COLOR
            );
        } else {
            graphics.enableScissor(viewX, viewY, viewX + viewWidth, viewY + viewHeight);
            graphics.pose().pushMatrix();
            graphics.pose().translate(viewX + this.panX, viewY + this.panY);
            graphics.pose().scale(this.zoom, this.zoom);
            this.renderConnections(graphics, this.layoutRoot);
            this.renderNodes(
                graphics,
                this.layoutRoot,
                this.toLocalX(mouseX, viewX),
                this.toLocalY(mouseY, viewY),
                mouseX,
                mouseY
            );
            graphics.pose().popMatrix();
            graphics.disableScissor();
        }

        graphics.text(
            this.font,
            Component.translatable("screen.tmct.tree.controls"),
            panelX + 8,
            panelY + panelHeight - 18,
            MUTED_COLOR,
            false
        );

        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (event.button() == 0 && this.layoutRoot != null && this.isInsideTreeViewport(event.x(), event.y())) {
            int localMouseX = this.toLocalX((int) event.x(), PANEL_X + PANEL_INSET);
            int localMouseY = this.toLocalY((int) event.y(), PANEL_TOP + PANEL_INSET);
            LayoutNode toggleNode = this.findToggleNode(this.layoutRoot, localMouseX, localMouseY);
            if (toggleNode != null) {
                this.toggleNode(toggleNode);
                return true;
            }
            this.dragging = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (!this.dragging || event.button() != 0 || this.layoutRoot == null) {
            return super.mouseDragged(event, dx, dy);
        }

        this.panX += (int) Math.round(dx);
        this.panY += (int) Math.round(dy);
        this.clampPan();
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.dragging = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (this.layoutRoot == null || !this.isInsideTreeViewport(x, y)) {
            return super.mouseScrolled(x, y, scrollX, scrollY);
        }

        if (scrollY != 0.0D) {
            this.zoomAt((int) x, (int) y, scrollY > 0.0D ? ZOOM_STEP : -ZOOM_STEP);
        } else if (scrollX != 0.0D) {
            this.panX += (int) Math.round(scrollX * PAN_STEP);
            this.clampPan();
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        boolean moved = switch (event.key()) {
            case 262 -> this.panBy(-PAN_STEP, 0);
            case 263 -> this.panBy(PAN_STEP, 0);
            case 264 -> this.panBy(0, -PAN_STEP);
            case 265 -> this.panBy(0, PAN_STEP);
            default -> false;
        };
        return moved || super.keyPressed(event);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    private boolean panBy(int dx, int dy) {
        if (this.layoutRoot == null) {
            return false;
        }
        this.panX += dx;
        this.panY += dy;
        this.clampPan();
        return true;
    }

    private void rebuildLayout() {
        this.layoutRoot = null;
        this.treeWidth = 0;
        this.treeHeight = 0;

        if (this.result.tree == null) {
            return;
        }

        LayoutNode root = this.buildLayoutNode(this.result.tree, "root", 0);
        this.measureSubtree(root);
        this.positionSubtree(root, CONTENT_PADDING_X, 0);
        this.layoutRoot = root;
        this.treeWidth = root.subtreeWidth + CONTENT_PADDING_X * 2;
        this.treeHeight = root.maxBottom + CONTENT_PADDING_TOP + CONTENT_PADDING_BOTTOM;
    }

    private LayoutNode buildLayoutNode(SimpleTreeCalculator.SimpleTreeNode node, String path, int depth) {
        RecipeInfo recipeInfo = this.resolveRecipeInfo(node);
        ItemStack outputStack = this.resolveItemStack(node.output == null ? null : node.output.item);
        String displayName = displayName(node.output);

        List<LayoutNode> children = new ArrayList<>();
        if (node.inputs != null) {
            for (int i = 0; i < node.inputs.size(); i++) {
                children.add(this.buildLayoutNode(node.inputs.get(i), path + "." + i, depth + 1));
            }
        }

        return new LayoutNode(path, depth, node, recipeInfo, outputStack, displayName, children);
    }

    private int measureSubtree(LayoutNode node) {
        if (!this.isExpanded(node)) {
            node.subtreeWidth = MIN_SUBTREE_WIDTH;
        } else if (node.children.isEmpty()) {
            node.subtreeWidth = MIN_SUBTREE_WIDTH;
        } else {
            int childrenWidth = 0;
            for (int i = 0; i < node.children.size(); i++) {
                childrenWidth += this.measureSubtree(node.children.get(i));
                if (i > 0) {
                    childrenWidth += SIBLING_GAP;
                }
            }
            node.subtreeWidth = Math.max(MIN_SUBTREE_WIDTH, childrenWidth);
        }
        return node.subtreeWidth;
    }

    private void positionSubtree(LayoutNode node, int left, int maxBottom) {
        int top = CONTENT_PADDING_TOP + node.depth * LAYER_HEIGHT;
        node.centerX = left + node.subtreeWidth / 2;
        node.top = top;
        node.maxBottom = Math.max(maxBottom, top + NODE_BOX_HEIGHT);
        if (!this.isExpanded(node)) {
            return;
        }

        int childrenWidth = 0;
        for (int i = 0; i < node.children.size(); i++) {
            childrenWidth += node.children.get(i).subtreeWidth;
            if (i > 0) {
                childrenWidth += SIBLING_GAP;
            }
        }

        int childLeft = left + Math.max(0, (node.subtreeWidth - childrenWidth) / 2);
        for (LayoutNode child : node.children) {
            this.positionSubtree(child, childLeft, node.maxBottom);
            node.maxBottom = Math.max(node.maxBottom, child.maxBottom);
            childLeft += child.subtreeWidth + SIBLING_GAP;
        }
    }

    private void renderConnections(GuiGraphicsExtractor graphics, LayoutNode node) {
        if (!node.children.isEmpty() && this.isExpanded(node)) {
            int parentBottom = node.top + NODE_BOX_HEIGHT;
            int branchY = node.top + LAYER_HEIGHT - 14;
            int connectorStartY = parentBottom;

            if (node.recipeInfo.hasDisplay()) {
                int badgeTop = parentBottom + RECIPE_BADGE_GAP;
                int badgeBottom = badgeTop + node.recipeInfo.badgeHeight();
                this.drawVertical(graphics, node.centerX, parentBottom, badgeBottom + 4);
                this.renderRecipeBadge(graphics, node, badgeTop);
                connectorStartY = badgeBottom + 4;
            }

            this.drawVertical(graphics, node.centerX, connectorStartY, branchY);

            int minChildX = node.children.getFirst().centerX;
            int maxChildX = node.children.getLast().centerX;
            graphics.horizontalLine(minChildX, maxChildX, branchY, LINE_COLOR);

            for (LayoutNode child : node.children) {
                this.drawVertical(graphics, child.centerX, branchY, child.top);
                this.renderConnections(graphics, child);
            }
        }
    }

    private void renderNodes(
        GuiGraphicsExtractor graphics,
        LayoutNode node,
        int localMouseX,
        int localMouseY,
        int screenMouseX,
        int screenMouseY
    ) {
        int slotX = node.centerX - ITEM_SLOT_SIZE / 2;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE, slotX, node.top, ITEM_SLOT_SIZE, ITEM_SLOT_SIZE);
        if (node.outputStack != null) {
            graphics.fakeItem(node.outputStack, slotX + 1, node.top + 1);
        }

        this.renderCountLabel(graphics, node);
        this.renderToggle(graphics, node);

        if (this.isMouseOverNode(node, localMouseX, localMouseY)) {
            this.renderNodeTooltip(graphics, node, screenMouseX, screenMouseY);
        } else if (this.isMouseOverToggle(node, localMouseX, localMouseY)) {
            this.renderToggleTooltip(graphics, node, screenMouseX, screenMouseY);
        } else if (this.isMouseOverRecipeBadge(node, localMouseX, localMouseY)) {
            this.renderRecipeTooltip(graphics, node, screenMouseX, screenMouseY);
        }

        if (this.isExpanded(node)) {
            for (LayoutNode child : node.children) {
                this.renderNodes(graphics, child, localMouseX, localMouseY, screenMouseX, screenMouseY);
            }
        }
    }

    private void renderCountLabel(GuiGraphicsExtractor graphics, LayoutNode node) {
        String text = "x" + (node.node.output == null ? 0L : node.node.output.count);
        int textWidth = this.font.width(text);
        int labelWidth = textWidth + 8;
        int left = node.centerX - labelWidth / 2;
        int top = node.top + ITEM_TEXT_Y - 1;
        int labelHeight = 12;
        graphics.fill(left, top, left + labelWidth, top + labelHeight, COUNT_FILL);
        graphics.outline(left, top, labelWidth, labelHeight, COUNT_BORDER);
        graphics.text(this.font, text, left + 4, top + 2, COUNT_TEXT_COLOR, false);
    }

    private void renderNodeTooltip(GuiGraphicsExtractor graphics, LayoutNode node, int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(node.displayName + " x" + (node.node.output == null ? 0L : node.node.output.count)));
        lines.add(Component.literal("Type: " + node.node.type));
        if (node.node.crafts != null) {
            lines.add(Component.literal("Crafts: " + node.node.crafts));
        }
        if (node.recipeInfo.machineName != null) {
            lines.add(Component.literal("Machine: " + node.recipeInfo.machineName));
        } else if (node.recipeInfo.categoryTitle != null) {
            lines.add(Component.literal("Category: " + node.recipeInfo.categoryTitle));
        }
        if (node.node.recipeType != null) {
            lines.add(Component.literal("Recipe type: " + node.node.recipeType));
        }

        ItemStack stack = node.outputStack == null ? ItemStack.EMPTY : node.outputStack;
        graphics.setComponentTooltipForNextFrame(this.font, lines, mouseX, mouseY, stack);
    }

    private void renderToggleTooltip(GuiGraphicsExtractor graphics, LayoutNode node, int mouseX, int mouseY) {
        if (node.children.isEmpty()) {
            return;
        }
        Component text = Component.literal(this.isExpanded(node) ? "Collapse subtree" : "Expand subtree");
        graphics.setTooltipForNextFrame(this.font, text, mouseX, mouseY);
    }

    private void renderRecipeTooltip(GuiGraphicsExtractor graphics, LayoutNode node, int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        if (node.recipeInfo.machineName != null) {
            lines.add(Component.literal(node.recipeInfo.machineName));
        }
        if (node.recipeInfo.categoryTitle != null) {
            lines.add(Component.literal(node.recipeInfo.categoryTitle));
        }
        if (node.node.recipeType != null) {
            lines.add(Component.literal(node.node.recipeType));
        }

        if (lines.isEmpty()) {
            return;
        }
        ItemStack stack = node.recipeInfo.iconStack == null ? ItemStack.EMPTY : node.recipeInfo.iconStack;
        graphics.setComponentTooltipForNextFrame(this.font, lines, mouseX, mouseY, stack);
    }

    private void renderRecipeBadge(GuiGraphicsExtractor graphics, LayoutNode node, int top) {
        int left = this.recipeBadgeLeft(node);
        if (node.recipeInfo.iconStack != null) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE, left, top, RECIPE_BADGE_SIZE, RECIPE_BADGE_SIZE);
            graphics.fakeItem(node.recipeInfo.iconStack, left + 1, top + 1);
            return;
        }

        if (node.recipeInfo.categoryTitle != null) {
            String label = node.recipeInfo.categoryTitle;
            int textWidth = Math.min(this.font.width(label), 120);
            int width = textWidth + 8;
            int right = left + width;
            graphics.fill(left, top, right, top + RECIPE_TEXT_HEIGHT + 4, LABEL_FILL);
            graphics.outline(left, top, width, RECIPE_TEXT_HEIGHT + 4, PANEL_BORDER);
            graphics.centeredText(this.font, Component.literal(label), left + width / 2, top + 2, ACCENT_COLOR);
        }
    }

    private void renderToggle(GuiGraphicsExtractor graphics, LayoutNode node) {
        if (node.children.isEmpty()) {
            return;
        }
        int left = this.toggleLeft(node);
        int top = this.toggleTop(node);
        graphics.fill(left, top, left + TOGGLE_SIZE, top + TOGGLE_SIZE, TOGGLE_FILL);
        graphics.outline(left, top, TOGGLE_SIZE, TOGGLE_SIZE, TOGGLE_BORDER);
        String label = this.isExpanded(node) ? "-" : "+";
        graphics.text(this.font, label, left + 3, top + 1, TOGGLE_TEXT_COLOR, false);
    }

    private int recipeBadgeLeft(LayoutNode node) {
        int badgeWidth = node.recipeInfo.badgeWidth(this.font);
        boolean placeLeft = node.centerX > this.treeWidth / 2;
        return placeLeft ? node.centerX - RECIPE_SIDE_GAP - badgeWidth : node.centerX + RECIPE_SIDE_GAP;
    }

    private void drawVertical(GuiGraphicsExtractor graphics, int x, int y0, int y1) {
        if (y1 >= y0) {
            graphics.fill(x, y0, x + 1, y1 + 1, LINE_COLOR);
        }
    }

    private boolean isMouseOverNode(LayoutNode node, int mouseX, int mouseY) {
        int left = node.centerX - NODE_BOX_HALF_WIDTH;
        int right = node.centerX + NODE_BOX_HALF_WIDTH;
        return mouseX >= left && mouseX <= right && mouseY >= node.top && mouseY <= node.top + NODE_BOX_HEIGHT;
    }

    private boolean isMouseOverToggle(LayoutNode node, int mouseX, int mouseY) {
        if (node.children.isEmpty()) {
            return false;
        }
        int left = this.toggleLeft(node);
        int top = this.toggleTop(node);
        return mouseX >= left && mouseX <= left + TOGGLE_SIZE && mouseY >= top && mouseY <= top + TOGGLE_SIZE;
    }

    private boolean isMouseOverRecipeBadge(LayoutNode node, int mouseX, int mouseY) {
        if (!node.recipeInfo.hasDisplay() || node.children.isEmpty()) {
            return false;
        }
        int top = node.top + NODE_BOX_HEIGHT + RECIPE_BADGE_GAP;
        int left = this.recipeBadgeLeft(node);
        int right = left + node.recipeInfo.badgeWidth(this.font);
        return mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= top + node.recipeInfo.badgeHeight();
    }

    private boolean isInsideTreeViewport(double mouseX, double mouseY) {
        int panelWidth = this.width - PANEL_X * 2;
        int panelHeight = this.height - PANEL_TOP - PANEL_BOTTOM;
        int viewX = PANEL_X + PANEL_INSET;
        int viewY = PANEL_TOP + PANEL_INSET;
        int viewWidth = panelWidth - PANEL_INSET * 2;
        int viewHeight = panelHeight - PANEL_INSET * 2;
        return mouseX >= viewX && mouseX < viewX + viewWidth && mouseY >= viewY && mouseY < viewY + viewHeight;
    }

    private void clampPan() {
        int panelWidth = this.width - PANEL_X * 2;
        int panelHeight = this.height - PANEL_TOP - PANEL_BOTTOM;
        int viewWidth = panelWidth - PANEL_INSET * 2;
        int viewHeight = panelHeight - PANEL_INSET * 2;
        int scaledTreeWidth = Math.round(this.treeWidth * this.zoom);
        int scaledTreeHeight = Math.round(this.treeHeight * this.zoom);

        if (scaledTreeWidth <= viewWidth) {
            this.panX = (viewWidth - scaledTreeWidth) / 2;
        } else {
            this.panX = Math.max(viewWidth - scaledTreeWidth, Math.min(0, this.panX));
        }

        if (scaledTreeHeight <= viewHeight) {
            this.panY = 0;
        } else {
            this.panY = Math.max(viewHeight - scaledTreeHeight, Math.min(0, this.panY));
        }
    }

    private int toLocalX(int screenX, int viewX) {
        return Math.round((screenX - viewX - this.panX) / this.zoom);
    }

    private int toLocalY(int screenY, int viewY) {
        return Math.round((screenY - viewY - this.panY) / this.zoom);
    }

    private void zoomAt(int mouseX, int mouseY, float delta) {
        float oldZoom = this.zoom;
        this.zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, this.zoom + delta));
        if (this.zoom == oldZoom) {
            return;
        }

        int viewX = PANEL_X + PANEL_INSET;
        int viewY = PANEL_TOP + PANEL_INSET;
        float localX = (mouseX - viewX - this.panX) / oldZoom;
        float localY = (mouseY - viewY - this.panY) / oldZoom;

        this.panX = Math.round(mouseX - viewX - localX * this.zoom);
        this.panY = Math.round(mouseY - viewY - localY * this.zoom);
        this.clampPan();
    }

    private boolean isExpanded(LayoutNode node) {
        return !this.collapsedPaths.contains(node.path);
    }

    private void toggleNode(LayoutNode node) {
        if (node.children.isEmpty()) {
            return;
        }
        if (this.collapsedPaths.contains(node.path)) {
            this.collapsedPaths.remove(node.path);
        } else {
            this.collapsedPaths.add(node.path);
        }
        this.rebuildLayout();
        this.clampPan();
    }

    private @Nullable LayoutNode findToggleNode(LayoutNode node, int localMouseX, int localMouseY) {
        if (this.isMouseOverToggle(node, localMouseX, localMouseY)) {
            return node;
        }
        if (!this.isExpanded(node)) {
            return null;
        }
        for (LayoutNode child : node.children) {
            LayoutNode found = this.findToggleNode(child, localMouseX, localMouseY);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private int toggleLeft(LayoutNode node) {
        int text = this.font.width("x" + (node.node.output == null ? 0L : node.node.output.count));
        int labelWidth = text + 8;
        return node.centerX + labelWidth / 2 + 4;
    }

    private int toggleTop(LayoutNode node) {
        return node.top + ITEM_TEXT_Y;
    }

    private RecipeInfo resolveRecipeInfo(SimpleTreeCalculator.SimpleTreeNode node) {
        if (!"recipe".equals(node.type) || node.recipeType == null || node.recipeId == null || this.minecraft.level == null) {
            return RecipeInfo.EMPTY;
        }

        String cacheKey = node.recipeType + "|" + node.recipeId;
        RecipeInfo cached = this.recipeInfoCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        RecipeData recipe = TmctClientRecipeService.getInstance()
            .findRecipeByDisplayId(this.minecraft.level.registryAccess(), node.recipeType, node.recipeId, this.includeHidden)
            .orElse(null);
        String categoryTitle = recipe != null ? recipe.categoryTitle : null;

        List<IngredientData> stations = this.stationsByRecipeType.computeIfAbsent(
            node.recipeType,
            recipeType -> TmctClientRecipeService.getInstance().findCraftingStations(
                this.minecraft.level.registryAccess(),
                recipeType,
                this.includeHidden
            )
        );

        IngredientData primaryStation = stations.stream()
            .filter(station -> station.item != null)
            .findFirst()
            .orElse(stations.stream().findFirst().orElse(null));
        ItemStack iconStack = stationIcon(primaryStation);
        String machineName = primaryStation == null ? null : displayName(primaryStation);

        RecipeInfo info = new RecipeInfo(iconStack, machineName, categoryTitle);
        this.recipeInfoCache.put(cacheKey, info);
        return info;
    }

    private @Nullable ItemStack resolveItemStack(@Nullable String itemIdValue) {
        if (itemIdValue == null) {
            return null;
        }
        Identifier itemId = Identifier.tryParse(itemIdValue);
        if (itemId == null) {
            return null;
        }
        var item = BuiltInRegistries.ITEM.getValue(itemId);
        if (item == null || item == Items.AIR) {
            return null;
        }
        return new ItemStack(item);
    }

    private Component headerTarget() {
        String targetName = this.target.displayName != null ? this.target.displayName : fallbackName(this.target.identifier);
        return Component.literal(targetName + " x" + this.target.craftAmount() + "  |  depth " + this.maxDepth);
    }

    private static @Nullable ItemStack stationIcon(@Nullable IngredientData station) {
        if (station == null || station.item == null) {
            return null;
        }
        Identifier itemId = Identifier.tryParse(station.item);
        if (itemId == null) {
            return null;
        }
        var item = BuiltInRegistries.ITEM.getValue(itemId);
        if (item == null || item == Items.AIR) {
            return null;
        }
        return new ItemStack(item);
    }

    private static String displayName(IngredientData ingredient) {
        if (ingredient.displayName != null && !ingredient.displayName.isBlank()) {
            return ingredient.displayName;
        }
        if (ingredient.identifier != null) {
            return ingredient.identifier;
        }
        if (ingredient.item != null) {
            return ingredient.item;
        }
        if (ingredient.fluid != null) {
            return ingredient.fluid;
        }
        return "unknown";
    }

    private static String fallbackName(@Nullable String identifier) {
        return identifier == null ? "unknown" : identifier;
    }

    private static String displayName(SimpleTreeCalculator.SimpleItem item) {
        if (item == null || item.item == null) {
            return "unknown";
        }
        Identifier itemId = Identifier.tryParse(item.item);
        if (itemId != null) {
            var resolved = BuiltInRegistries.ITEM.getValue(itemId);
            if (resolved != null && resolved != Items.AIR) {
                return new ItemStack(resolved).getDisplayName().getString();
            }
        }
        return item.item;
    }

    private static final class LayoutNode {
        private final String path;
        private final int depth;
        private final SimpleTreeCalculator.SimpleTreeNode node;
        private final RecipeInfo recipeInfo;
        private final @Nullable ItemStack outputStack;
        private final String displayName;
        private final List<LayoutNode> children;
        private int subtreeWidth;
        private int centerX;
        private int top;
        private int maxBottom;

        private LayoutNode(
            String path,
            int depth,
            SimpleTreeCalculator.SimpleTreeNode node,
            RecipeInfo recipeInfo,
            @Nullable ItemStack outputStack,
            String displayName,
            List<LayoutNode> children
        ) {
            this.path = path;
            this.depth = depth;
            this.node = node;
            this.recipeInfo = recipeInfo;
            this.outputStack = outputStack;
            this.displayName = displayName;
            this.children = children;
        }
    }

    private record RecipeInfo(@Nullable ItemStack iconStack, @Nullable String machineName, @Nullable String categoryTitle) {
        private static final RecipeInfo EMPTY = new RecipeInfo(null, null, null);

        private boolean hasDisplay() {
            return this.iconStack != null || this.categoryTitle != null;
        }

        private int badgeHeight() {
            return this.iconStack != null ? RECIPE_BADGE_SIZE : RECIPE_TEXT_HEIGHT + 4;
        }

        private int badgeWidth(net.minecraft.client.gui.Font font) {
            if (this.iconStack != null) {
                return RECIPE_BADGE_SIZE;
            }
            if (this.categoryTitle != null) {
                return Math.min(font.width(this.categoryTitle), 120) + 8;
            }
            return 0;
        }
    }
}
