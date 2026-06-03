package github.alecsio.mmceaddons.client.gui;

import github.alecsio.mmceaddons.ModularMachineryAddons;
import github.alecsio.mmceaddons.common.hatch.appeng.itembus.AdvancedMEItemInputBus;
import github.alecsio.mmceaddons.common.hatch.appeng.itembus.ContainerAdvancedMEItemInputBus;
import github.alecsio.mmceaddons.common.network.AdvancedMESettingsMessage;
import github.alecsio.mmceaddons.common.network.AdvancedMERSyncMessage;
import hellfirepvp.modularmachinery.client.gui.GuiContainerBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

/**
 * GUI for the Advanced ME Item Input Bus.
 * <p>
 * Left side: polling rate text field and force rescan button (config area cleared).
 * Right side: read-only 4x4 grid showing drained items from AE2 (SlotDisabled).
 */
public class GuiAdvancedMEItemInputBus extends GuiContainerBase<ContainerAdvancedMEItemInputBus> {

    /** Custom background texture for the Advanced ME Item Input Bus. */
    private static final ResourceLocation GUI_TEXTURE = new ResourceLocation(
            "modularmachineryaddons", "textures/gui/advancedmeiteminputbus.png");

    /** Tile position, stored from block activation. */
    private final BlockPos pos;

    // ---- Grid positions (matching advancedmeiteminputbus.png texture) ----
    private static final int LEFT_GRID_X = 8;
    private static final int RIGHT_GRID_X = 90;
    private static final int GRID_Y = 50;
    private static final int GRID_COLS = 4;
    private static final int GRID_ROWS = 4;

    // ---- Polling rate text field (above left grid) ----
    private static final int POLLING_FIELD_X = 8;
    private static final int POLLING_FIELD_Y = 50;
    private static final int POLLING_FIELD_W = 70;
    private static final int POLLING_FIELD_H = 14;

    // ---- Force rescan button (below polling field) ----
    private static final int FORCE_RESCAN_X = POLLING_FIELD_X;
    private static final int FORCE_RESCAN_Y = POLLING_FIELD_Y;
    private static final int FORCE_RESCAN_W = 84;
    private static final int FORCE_RESCAN_H = 19;

    /** Text field for polling rate input. */
    private GuiTextField pollingTextField;

    /** Force rescan button. */
    private GuiButton forceRescanButton;

    /** Whether the text field is currently active (focused). */
    private boolean pollingFieldActive = false;

    public GuiAdvancedMEItemInputBus(AdvancedMEItemInputBus owner, net.minecraft.entity.player.EntityPlayer player) {
        super(new ContainerAdvancedMEItemInputBus(owner, player));
        this.pos = owner.getPos();
    }


    @Override
    protected void setWidthHeight() {
        this.xSize = 176;
        this.ySize = 208;
    }

    @Override
    public void initGui() {
        super.initGui();

        FontRenderer font = Minecraft.getMinecraft().fontRenderer;

        // Polling rate text field — replaces the polling rate display
        pollingTextField = new GuiTextField(0, font,
                POLLING_FIELD_X,
                POLLING_FIELD_Y + (POLLING_FIELD_H - 8) / 2 - 2,
                POLLING_FIELD_W,
                POLLING_FIELD_H);

        pollingTextField.setMaxStringLength(4);
        pollingTextField.setFocused(false);
        pollingTextField.setVisible(true);

        // Force rescan button — below the polling field, aligned with Polling Rate label
        forceRescanButton = new GuiButton(1,
                guiLeft + FORCE_RESCAN_X,
                guiTop + FORCE_RESCAN_Y + (FORCE_RESCAN_H - 8) / 2 + 32,
                FORCE_RESCAN_W,
                FORCE_RESCAN_H,
                "Force Rescan");
        buttonList.add(forceRescanButton);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        // Draw custom background texture
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        mc.getTextureManager().bindTexture(GUI_TEXTURE);
        int guiLeft = (width - xSize) / 2;
        int guiTop = (height - ySize) / 2;
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);

        // Draw buttons (includes force rescan button)
        for (GuiButton button : buttonList) {
            button.drawButton(mc, mouseX, mouseY, partialTicks);
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        FontRenderer font = fontRenderer;

        // Draw title
        font.drawString("Advanced ME Input Bus", 8, 6, 0x404040);
        font.drawString("Config", 8, 6 + 11 + 7, 0x404040);
        font.drawString("Stored Items", 97, 6 + 11 + 7, 0x404040);

        // Draw "Polling Rate:" label above the text field
        font.drawString("Polling Rate:", POLLING_FIELD_X,
                POLLING_FIELD_Y - 10, 0x404040);

        if (pollingFieldActive && pollingTextField != null) {
            // Text field is active — it replaces the display; drawTextBox handles its own rendering
            pollingTextField.drawTextBox();
        } else if (pollingTextField != null) {
            // Draw static display text when field is not active
            String displayText = String.valueOf(container.getPollingInterval());
            font.drawString(displayText, POLLING_FIELD_X,
                    POLLING_FIELD_Y + (POLLING_FIELD_H - 8) / 2, 0x404040);
        }
        // Draw player inventory label
        font.drawString("Inventory", 8, ySize - 96 + 2, 0x404040);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws java.io.IOException {
        // Check if clicking on the polling rate display area
        boolean inPollingArea = mouseX >= guiLeft + POLLING_FIELD_X
                && mouseX <= guiLeft + POLLING_FIELD_X + POLLING_FIELD_W
                && mouseY >= guiTop + POLLING_FIELD_Y
                && mouseY <= guiTop + POLLING_FIELD_Y + POLLING_FIELD_H;

        if (inPollingArea) {
            // Activate the text field
            pollingFieldActive = true;
            pollingTextField.setFocused(true);
            pollingTextField.setText(String.valueOf(container.getPollingInterval()));
            return;
        }

        // If clicking elsewhere and field is active, deactivate it
        if (pollingFieldActive) {
            pollingFieldActive = false;
            pollingTextField.setFocused(false);
            // Send the current value to server on blur
            sendPollingInterval();
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws java.io.IOException {
        // If polling text field is active, handle input there
        if (pollingFieldActive && pollingTextField != null) {
            if (pollingTextField.textboxKeyTyped(typedChar, keyCode)) {
                // Value changed — validate and send to server
                String text = pollingTextField.getText();
                try {
                    int value = Integer.parseInt(text);
                    if (value >= AdvancedMEItemInputBus.MIN_POLLING_INTERVAL_TICKS
                            && value <= AdvancedMEItemInputBus.MAX_POLLING_INTERVAL_TICKS) {
                        sendPollingInterval();
                    } else {
                        // Out of range — revert to valid value
                        pollingTextField.setText(String.valueOf(container.getPollingInterval()));
                    }
                } catch (NumberFormatException e) {
                    // Invalid number — don't send, let user correct it
                }
                return;
            }
        }

        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        // Keep text field in sync if server changed the value
        if (pollingTextField != null && !pollingFieldActive) {
            String currentText = pollingTextField.getText();
            String expectedText = String.valueOf(container.getPollingInterval());
            if (!currentText.equals(expectedText)) {
                pollingTextField.setText(expectedText);
            }
        }
    }

    @Override
    public void onGuiClosed() {
        // Send final value to server before closing
        if (pollingFieldActive) {
            sendPollingInterval();
            pollingFieldActive = false;
        }
        super.onGuiClosed();
    }

    @Override
    protected void actionPerformed(GuiButton button) throws java.io.IOException {
        if (button == forceRescanButton) {
            // Send force rescan message to server
            AdvancedMEItemInputBus bus = container.getAdvancedOwner();
            if (bus != null && pos != null) {
                ModularMachineryAddons.INSTANCE.sendToServer(new AdvancedMERSyncMessage(pos));
            }
        }
    }

    /**
     * Sends the current polling interval value to the server.
     */
    private void sendPollingInterval() {
        if (pollingTextField != null && pos != null) {
            String text = pollingTextField.getText();
            try {
                int value = Integer.parseInt(text);
                // Clamp to valid range before sending
                value = Math.max(AdvancedMEItemInputBus.MIN_POLLING_INTERVAL_TICKS,
                        Math.min(AdvancedMEItemInputBus.MAX_POLLING_INTERVAL_TICKS, value));
                container.setPollingInterval(value);
                ModularMachineryAddons.INSTANCE.sendToServer(new AdvancedMESettingsMessage(value, pos));
            } catch (NumberFormatException e) {
                // Invalid input — revert to current valid value
                pollingTextField.setText(String.valueOf(container.getPollingInterval()));
            }
        }
    }
}
